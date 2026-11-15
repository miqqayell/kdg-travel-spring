package be.kdg.integration4.spring_backend.controller.web.dto;

public class ActivityOverviewItem {
    private final ActivityDTO activity;
    private final ActivityFriendAttendanceSummary friendAttendanceSummary;
    private final long activeTravelGroupCount;

    public ActivityOverviewItem(ActivityDTO activity,
                                ActivityFriendAttendanceSummary friendAttendanceSummary,
                                long activeTravelGroupCount) {
        this.activity = activity;
        this.friendAttendanceSummary = friendAttendanceSummary;
        this.activeTravelGroupCount = activeTravelGroupCount;
    }

    public ActivityDTO getActivity() {
        return activity;
    }

    public ActivityFriendAttendanceSummary getFriendAttendanceSummary() {
        return friendAttendanceSummary;
    }

    public long getActiveTravelGroupCount() {
        return activeTravelGroupCount;
    }

    public boolean hasAttendingFriends() {
        return friendAttendanceSummary.hasAttendingFriends();
    }
}
