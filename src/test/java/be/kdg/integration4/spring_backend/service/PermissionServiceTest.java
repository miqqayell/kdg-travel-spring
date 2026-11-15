package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceTest {

    private final PermissionService permissions = new PermissionService();

    private static User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private static Activity activityOwnedBy(User owner) {
        Activity activity = new Activity();
        activity.setCreatedByUser(owner);
        return activity;
    }

    @Test
    void ownerCanEditAndDeleteOwnActivity() {
        User owner = user(1, Role.STUDENT);
        Activity activity = activityOwnedBy(owner);

        assertTrue(permissions.canEditActivity(owner, activity));
        assertTrue(permissions.canDeleteActivity(owner, activity));
    }

    @Test
    void otherStudentCannotEditSomeoneElsesActivity() {
        Activity activity = activityOwnedBy(user(1, Role.STUDENT));
        User other = user(2, Role.STUDENT);

        assertFalse(permissions.canEditActivity(other, activity));
        assertFalse(permissions.canDeleteActivity(other, activity));
    }

    @Test
    void moderatorsCanEditAnyActivity() {
        Activity activity = activityOwnedBy(user(1, Role.STUDENT));

        assertTrue(permissions.canEditActivity(user(9, Role.MODERATOR), activity));
    }

    @Test
    void anonymousUserHasNoPermissions() {
        Activity activity = activityOwnedBy(user(1, Role.STUDENT));

        assertFalse(permissions.canEditActivity(null, activity));
        assertFalse(permissions.canCreateActivity(null));
        assertFalse(permissions.canAccessAdminDashboard(null));
    }

    @Test
    void onlyStaffRolesReachTheAdminDashboard() {
        assertFalse(permissions.canAccessAdminDashboard(user(1, Role.STUDENT)));
        assertTrue(permissions.canAccessAdminDashboard(user(2, Role.MODERATOR)));
        assertTrue(permissions.canAccessAdminDashboard(user(3, Role.ADMIN)));
        assertFalse(permissions.canViewAdminAnalytics(user(2, Role.MODERATOR)));
    }

    @Test
    void adminCanPromoteToModeratorButNotToAdmin() {
        User admin = user(1, Role.ADMIN);

        assertTrue(permissions.canChangeUserRole(admin, Role.STUDENT, Role.MODERATOR));
        assertFalse(permissions.canChangeUserRole(admin, Role.STUDENT, Role.ADMIN));
        assertFalse(permissions.canChangeUserRole(admin, Role.ADMIN, Role.STUDENT));
    }

    @Test
    void superAdminCanManageAdminsButModeratorsCannotChangeRoles() {
        assertTrue(permissions.canChangeUserRole(user(1, Role.SUPERADMIN), Role.MODERATOR, Role.ADMIN));
        assertFalse(permissions.canChangeUserRole(user(2, Role.MODERATOR), Role.STUDENT, Role.MODERATOR));
    }
}
