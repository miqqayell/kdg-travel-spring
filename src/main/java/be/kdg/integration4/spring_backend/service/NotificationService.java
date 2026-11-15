package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.*;
import be.kdg.integration4.spring_backend.domain.enums.Badge;
import be.kdg.integration4.spring_backend.domain.enums.NotificationType;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import be.kdg.integration4.spring_backend.repository.ActivityParticipationRepository;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.NotificationRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ActivityParticipationRepository activityParticipationRepository;
    private final ActivityRepository activityRepository;
    private final TravelGroupRepository travelGroupRepository;

    // Triggered manually when an activity details change
    @Transactional
    public void notifyParticipantsOnActivityUpdate(Activity activity) {
        Set<Long> notifiedUserIds = new HashSet<>(); // Prevents duplicate notifications

        // 1. Notify Direct Participants
        List<ActivityParticipation> participations = activityParticipationRepository.findByActivityId(activity.getId());
        for (ActivityParticipation participation : participations) {
            User user = participation.getUser();
            if (notifiedUserIds.add(user.getId())) {
                createActivityUpdateNotification(user, activity, "Activity Updated: " + activity.getTitle(), "An activity you are participating in has been updated. Check the new details!");
            }
        }

        // 2. Notify Travel Group Members
        List<TravelGroup> groups = travelGroupRepository.findByActivityIdAndStatusOrderById(activity.getId(), TravelGroupStatus.ACTIVE);
        for (TravelGroup group : groups) {
            if (group.getMemberships() != null) {
                for (TravelGroupMembership membership : group.getMemberships()) {
                    User user = membership.getUser();
                    if (notifiedUserIds.add(user.getId())) {
                        createActivityUpdateNotification(user, activity, "Activity Updated: " + activity.getTitle(), "An activity you are participating in has been updated. Check the new details!");
                    }
                }
            }
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId);
        for (Notification note : notifications) {
            if (!note.isRead()) {
                note.setRead(true);
            }
        }
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void notifyActivityCancelled(Activity activity) {
        Set<Long> notifiedUserIds = new HashSet<>();

        // Get all participants
        List<ActivityParticipation> participations = activityParticipationRepository.findByActivityId(activity.getId());
        for (ActivityParticipation participation : participations) {
            User user = participation.getUser();
            // Don't notify the creator that they deleted their own event
            if (!user.getId().equals(activity.getCreatedByUser().getId()) && notifiedUserIds.add(user.getId())) {
                createActivityUpdateNotification(user, activity, "Event Cancelled: " + activity.getTitle(), "An event you planned to attend has been cancelled by the host.");
            }
        }

        // Get all group members
        List<TravelGroup> groups = travelGroupRepository.findByActivityIdAndStatusOrderById(activity.getId(), TravelGroupStatus.ACTIVE);
        for (TravelGroup group : groups) {
            if (group.getMemberships() != null) {
                for (TravelGroupMembership membership : group.getMemberships()) {
                    User user = membership.getUser();
                    if (!user.getId().equals(activity.getCreatedByUser().getId()) && notifiedUserIds.add(user.getId())) {
                        createActivityUpdateNotification(user, activity, "Event Cancelled: " + activity.getTitle(), "An event your group planned to attend has been cancelled.");
                    }
                }
            }
        }
    }

    // Runs automatically every hour to check for upcoming activities
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void notifyUpcomingActivities() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime within24Hours = now.plusDays(1);

        List<Activity> upcomingActivities = activityRepository.findByStartTimeBetween(now, within24Hours);

        for (Activity activity : upcomingActivities) {
            Set<Long> notifiedUserIds = new HashSet<>();

            // 1. Notify Direct Participants
            List<ActivityParticipation> participations = activityParticipationRepository.findByActivityId(activity.getId());
            for (ActivityParticipation participation : participations) {
                User user = participation.getUser();
                if (notifiedUserIds.add(user.getId())) {
                    createActivityUpdateNotification(user, activity, "Upcoming Activity: " + activity.getTitle(), "Reminder: Your activity '" + activity.getTitle() + "' is starting soon!");
                }
            }

            // 2. Notify Travel Group Members
            List<TravelGroup> groups = travelGroupRepository.findByActivityIdAndStatusOrderById(activity.getId(), TravelGroupStatus.ACTIVE);
            for (TravelGroup group : groups) {
                if (group.getMemberships() != null) {
                    for (TravelGroupMembership membership : group.getMemberships()) {
                        User user = membership.getUser();
                        if (notifiedUserIds.add(user.getId())) {
                            createActivityUpdateNotification(user, activity, "Upcoming Activity: " + activity.getTitle(), "Reminder: Your activity '" + activity.getTitle() + "' is starting soon!");
                        }
                    }
                }
            }
        }
    }

    // Triggered when someone joins a travel group
    @Transactional
    public void notifyCreatorOnGroupJoin(TravelGroup group, User joiningUser) {
        if (group.getCreatedByUser().getId().equals(joiningUser.getId())) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientUser(group.getCreatedByUser());
        notification.setNotificationType(NotificationType.GROUP_JOIN);
        notification.setTitle("New Group Member!");
        notification.setDescription(joiningUser.getUsername() + " has just joined your travel group: '" + group.getTitle() + "'.");
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId);
    }

    private void createActivityUpdateNotification(User user, Activity activity, String title, String description) {
        Notification notification = new Notification();
        notification.setRecipientUser(user);
        notification.setNotificationType(NotificationType.ACTIVITY_UPDATE);
        notification.setTitle(title);
        notification.setDescription(description);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setReferenceId(activity.getId());

        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyBuddyMatch(User recipient, User buddy, Activity activity) {
        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setNotificationType(NotificationType.BUDDY_MATCH);
        notification.setTitle("Travel buddy match: " + activity.getTitle());
        notification.setDescription(buddy.getUsername() + " is also going to '" + activity.getTitle() + "'. You could travel together!");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setReferenceId(activity.getId());
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyActivityInvite(User sender, User recipient, Activity activity) {
        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setNotificationType(NotificationType.ACTIVITY_INVITE);
        notification.setTitle("Travel invite: " + activity.getTitle());
        notification.setDescription(sender.getUsername() + " thinks you'd be a good travel companion for '" + activity.getTitle() + "'.");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setReferenceId(activity.getId());
        notificationRepository.save(notification);
    }

    // Triggered when a user sends a friend request
    @Transactional
    public void notifyFriendRequest(User sender, User recipient) {
        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setNotificationType(NotificationType.FRIEND_REQUEST);
        notification.setTitle("New Friend Request");
        notification.setDescription(sender.getUsername() + " has sent you a friend request!");
        notification.setCreatedAt(LocalDateTime.now());

        // Save the sender's ID so clicking the notification can route to their profile
        notification.setReferenceId(sender.getId());

        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyBadgeEarned(User user, Badge badge) {
        Notification notification = new Notification();
        notification.setRecipientUser(user);
        notification.setNotificationType(NotificationType.ACTIVITY_UPDATE); // Or create a new BADGE_EARNED type!
        notification.setTitle("🏆 New Badge Unlocked!");
        notification.setDescription("You earned the '" + badge.getDisplayName() + "' badge: " + badge.getDescription());
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyUserWarning(User user, Report report) {
        Notification notification = new Notification();
        notification.setRecipientUser(user);
        notification.setNotificationType(NotificationType.WARNING);
        notification.setTitle("Warning from moderation team");
        notification.setDescription("You received a warning after a report review. Reason: " + report.getReason());
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyReportProcessed(Report report) {
        Notification notification = new Notification();

        notification.setRecipientUser(report.getReporterUser());
        notification.setTitle("Your report has been processed");
        notification.setDescription("A moderator has reviewed your report and marked it as resolved.");
        notification.setNotificationType(NotificationType.REPORT_PROCESSED);
        notification.setReferenceId(report.getId());
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);

        notificationRepository.save(notification);
    }

}

