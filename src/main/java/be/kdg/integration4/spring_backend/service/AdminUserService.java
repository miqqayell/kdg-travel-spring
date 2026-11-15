package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void suspendUser(Long targetUserId, int days, String actingUserOriginalId) {
        User actingUser = getUserByOriginalId(actingUserOriginalId);
        User targetUser = getUserById(targetUserId);

        if (!permissionService.canSuspendOrBanUsers(actingUser)) {
            throw new AccessDeniedException("You are not allowed to suspend users.");
        }

        preventSelfAction(actingUser, targetUser);

        targetUser.setStatus(UserStatus.SUSPENDED);
        targetUser.setSuspendedUntil(LocalDateTime.now().plusDays(days));
    }

    @Transactional
    public void banUser(Long targetUserId, String actingUserOriginalId) {
        User actingUser = getUserByOriginalId(actingUserOriginalId);
        User targetUser = getUserById(targetUserId);

        if (!permissionService.canSuspendOrBanUsers(actingUser)) {
            throw new AccessDeniedException("You are not allowed to ban users.");
        }

        preventSelfAction(actingUser, targetUser);

        targetUser.setStatus(UserStatus.BANNED);
        targetUser.setSuspendedUntil(null);
    }

    @Transactional
    public void activateUser(Long targetUserId, String actingUserOriginalId) {
        User actingUser = getUserByOriginalId(actingUserOriginalId);
        User targetUser = getUserById(targetUserId);

        if (!permissionService.canSuspendOrBanUsers(actingUser)) {
            throw new AccessDeniedException("You are not allowed to reactivate users.");
        }

        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setSuspendedUntil(null);
    }

    @Transactional
    public void changeRole(Long targetUserId, Role newRole, String actingUserOriginalId) {
        User actingUser = getUserByOriginalId(actingUserOriginalId);
        User targetUser = getUserById(targetUserId);

        preventSelfAction(actingUser, targetUser);

        if (!permissionService.canChangeUserRole(actingUser, targetUser.getRole(), newRole)) {
            throw new AccessDeniedException("You are not allowed to change this user's role.");
        }

        targetUser.setRole(newRole);
    }

    @Transactional
    public void deleteAdminOrModerator(Long targetUserId, String actingUserOriginalId) {
        User actingUser = getUserByOriginalId(actingUserOriginalId);
        User targetUser = getUserById(targetUserId);

        preventSelfAction(actingUser, targetUser);

        if (!permissionService.canDeleteAdminOrModerator(actingUser, targetUser)) {
            throw new AccessDeniedException("Only superadmins can delete admin or moderator accounts.");
        }

        userRepository.delete(targetUser);
    }

    private User getUserByOriginalId(String originalId) {
        return userRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void preventSelfAction(User actingUser, User targetUser) {
        if (actingUser.getId().equals(targetUser.getId())) {
            throw new BadRequestException("You cannot perform this action on your own account.");
        }
    }
}
