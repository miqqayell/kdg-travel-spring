package be.kdg.integration4.spring_backend.controller.web.dto;

import java.util.List;

public class ActivityFriendAttendanceSummary {
    public static final class Attendee {
        private final Long userId;
        private final String username;
        private final String profilePictureUrl;

        public Attendee(Long userId, String username, String profilePictureUrl) {
            this.userId = userId;
            this.username = username;
            this.profilePictureUrl = profilePictureUrl;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getProfilePictureUrl() {
            return profilePictureUrl;
        }
    }

    private static final int PREVIEW_LIMIT = 3;

    private final List<Attendee> attendingFriends;

    public ActivityFriendAttendanceSummary(List<Attendee> attendingFriends) {
        this.attendingFriends = List.copyOf(attendingFriends);
    }

    public static ActivityFriendAttendanceSummary empty() {
        return new ActivityFriendAttendanceSummary(List.of());
    }

    public List<Attendee> getPreviewAttendees() {
        return attendingFriends.stream()
                .limit(PREVIEW_LIMIT)
                .toList();
    }

    public int getAttendeeCount() {
        return attendingFriends.size();
    }

    public int getRemainingAttendeeCount() {
        return Math.max(0, attendingFriends.size() - PREVIEW_LIMIT);
    }

    public boolean hasAttendingFriends() {
        return !attendingFriends.isEmpty();
    }
}
