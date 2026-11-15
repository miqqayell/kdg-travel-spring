package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProvisioningUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserRepository userRepository;

    public ProvisioningUserService(UserRepository userRepository) {
        this.delegate = new OidcUserService();
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Let Spring do the heavy lifting of talking to Google
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String originalUserId = oidcUser.getSubject(); // This is Google's unique user ID
        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();
        String pictureUrl = oidcUser.getPicture();


        // 1. Verify it's a KdG email
        if (!User.isEmailValid(email)) {
            throw new OAuth2AuthenticationException("Invalid email address. Must be a KdG account: " + email);
        }

        // 2. Find the user in our DB, or create a new one
        User user = userRepository.findByOriginalId(originalUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setOriginalId(originalUserId);

                    // Assign role based on email type using the newly imported Role enum!
                    if (email.endsWith("@student.kdg.be")) {
                        newUser.setRole(Role.STUDENT);
                    } else {
                        newUser.setRole(Role.TEACHER);
                    }
                    newUser.setStatus(be.kdg.integration4.spring_backend.domain.enums.UserStatus.ACTIVE);
                    return newUser;
                });

        // 3. Update info (in case they changed their name on Google)
        user.setEmail(email);
        user.setUsername(fullName);
        user.setProfilePictureUrl(pictureUrl);

        user.getBadges().clear();

        // 4. Check whether account is suspended or banned
        if (user.getStatus() == UserStatus.BANNED) {
            throw new OAuth2AuthenticationException("Your account has been banned.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                throw new OAuth2AuthenticationException("Your account is suspended until " + user.getSuspendedUntil());
            }

            user.setStatus(UserStatus.ACTIVE);
            user.setSuspendedUntil(null);
        }

        // Save to PostgreSQL
        userRepository.save(user);

        return oidcUser;
    }
}