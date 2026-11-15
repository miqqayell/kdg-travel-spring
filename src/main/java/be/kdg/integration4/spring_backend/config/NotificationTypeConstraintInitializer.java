package be.kdg.integration4.spring_backend.config;

import be.kdg.integration4.spring_backend.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps the {@code user_notifications.notification_type} check constraint in sync
 * with the {@link NotificationType} enum.
 *
 * <p>Hibernate's {@code ddl-auto=update} generates this constraint once but never
 * alters it afterwards, so adding a new enum value would otherwise be rejected on
 * existing databases. Rebuilding it from the enum on startup makes the schema
 * self-healing for everyone who pulls the code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTypeConstraintInitializer implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "user_notifications_notification_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        String allowedValues = Arrays.stream(NotificationType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));

        try {
            jdbcTemplate.execute(
                    "ALTER TABLE user_notifications DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            jdbcTemplate.execute(
                    "ALTER TABLE user_notifications ADD CONSTRAINT " + CONSTRAINT_NAME
                            + " CHECK (notification_type IN (" + allowedValues + "))");
        } catch (Exception e) {
            log.warn("Could not sync {} constraint: {}", CONSTRAINT_NAME, e.getMessage());
        }
    }
}
