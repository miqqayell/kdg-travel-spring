package be.kdg.integration4.spring_backend.config;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof OidcUser oidcUser) {

            User user = userRepository.findByOriginalId(oidcUser.getSubject()).orElse(null);

            if (user != null) {
                if (user.getStatus() == UserStatus.BANNED) {
                    SecurityContextHolder.clearContext();
                    request.getSession().invalidate();
                    response.sendRedirect("/login?banned");
                    return;
                }

                if (user.getStatus() == UserStatus.SUSPENDED) {
                    if (user.getSuspendedUntil() != null
                            && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                        SecurityContextHolder.clearContext();
                        request.getSession().invalidate();
                        response.sendRedirect("/login?suspended");
                        return;
                    }

                    user.setStatus(UserStatus.ACTIVE);
                    user.setSuspendedUntil(null);
                    userRepository.save(user);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
