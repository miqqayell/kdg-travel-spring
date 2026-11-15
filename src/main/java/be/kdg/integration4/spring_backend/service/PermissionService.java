package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public boolean canEditActivity(User user, Activity activity) {
        return canEditOwnedResource(user, activity != null ? activity.getCreatedByUser() : null);
    }

    public boolean canDeleteActivity(User user, Activity activity) {
        return canDeleteOwnedResource(user, activity != null ? activity.getCreatedByUser() : null);
    }

    public boolean canEditTravelGroup(User user, TravelGroup travelGroup) {
        return canEditOwnedResource(user, travelGroup != null ? travelGroup.getCreatedByUser() : null);
    }

    public boolean canDeleteTravelGroup(User user, TravelGroup travelGroup) {
        return canDeleteOwnedResource(user, travelGroup != null ? travelGroup.getCreatedByUser() : null);
    }

    public boolean canAccessAdminDashboard(User user) {
        return isModerator(user) || isAdmin(user) || isSuperAdmin(user);
    }

    public boolean canCreateActivity(User user) {
        return user != null;
    }

    public boolean canCreateTravelGroup(User user) {
        return user != null;
    }

    private boolean canEditOwnedResource(User user, User owner) {
        if (user == null || owner == null) return false;

        if (isSuperAdmin(user)) return true;
        if (isAdmin(user)) return true;
        if (isModerator(user)) return true;

        return isOwner(user, owner);
    }

    private boolean canDeleteOwnedResource(User user, User owner) {
        if (user == null || owner == null) return false;

        if (isSuperAdmin(user)) return true;
        if (isAdmin(user)) return true;
        if (isModerator(user)) return true;

        return isOwner(user, owner);
    }

    private boolean isOwner(User user, User owner) {
        return user.getId() != null
                && owner.getId() != null
                && user.getId().equals(owner.getId());
    }

    private boolean isModerator(User user) {
        return hasRole(user, "MODERATOR");
    }

    private boolean isAdmin(User user) {
        return hasRole(user, "ADMIN");
    }

    private boolean isSuperAdmin(User user) {
        return hasRole(user, "SUPERADMIN");
    }

    private boolean hasRole(User user, String roleName) {
        if (user == null || user.getRole() == null) return false;

        return user.getRole().name().equalsIgnoreCase(roleName);
    }

    public boolean canViewAdminAnalytics(User user) {
        return isAdmin(user) || isSuperAdmin(user);
    }

    public boolean canViewPersonalAnalytics(User user) {
        return user != null;
    }

    public boolean canSuspendOrBanUsers(User user) {
        return isModerator(user) || isAdmin(user) || isSuperAdmin(user);
    }

    public boolean canChangeUserRole(User actingUser, Role targetRole, Role newRole) {
        if (actingUser == null || actingUser.getRole() == null || targetRole == null || newRole == null) {
            return false;
        }

        if (isSuperAdmin(actingUser)) {
            boolean targetAllowed = targetRole == Role.STUDENT
                    || targetRole == Role.MODERATOR
                    || targetRole == Role.ADMIN;

            boolean newRoleAllowed = newRole == Role.STUDENT
                    || newRole == Role.MODERATOR
                    || newRole == Role.ADMIN;

            return targetAllowed && newRoleAllowed;
        }

        if (isAdmin(actingUser)) {
            boolean targetAllowed = targetRole == Role.STUDENT
                    || targetRole == Role.MODERATOR;

            boolean newRoleAllowed = newRole == Role.STUDENT
                    || newRole == Role.MODERATOR;

            return targetAllowed && newRoleAllowed;
        }

        return false;
    }

    public boolean canDeleteAdminOrModerator(User actingUser, User targetUser) {
        if (!isSuperAdmin(actingUser)) {
            return false;
        }

        return targetUser.getRole() == Role.ADMIN || targetUser.getRole() == Role.MODERATOR;
    }
}
