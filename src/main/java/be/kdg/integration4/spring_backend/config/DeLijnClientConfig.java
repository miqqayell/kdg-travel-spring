package be.kdg.integration4.spring_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({DeLijnApiProperties.class, PedestrianRoutingProperties.class})
public class DeLijnClientConfig {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public HttpClient deLijnHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }
}
