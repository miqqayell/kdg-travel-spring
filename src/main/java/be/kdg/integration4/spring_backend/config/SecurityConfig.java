package be.kdg.integration4.spring_backend.config;

import be.kdg.integration4.spring_backend.service.ProvisioningUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GoogleCloudOrganizationDomainAuthorizationRequestResolver googleResolver;
    private final ProvisioningUserService provisioningUserService;
    private final AccountStatusFilter accountStatusFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/ws-chat/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/ws-chat/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .failureUrl("/login?error")
                        .defaultSuccessUrl("/activities", true)
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(googleResolver)
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(provisioningUserService)
                        )
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .addFilterAfter(accountStatusFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}