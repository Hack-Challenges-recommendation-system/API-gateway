package team.nofantasy.recommendationsystem.apigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class, CorsProperties.class})
public class GatewayConfiguration {
    /**
     * Answers CORS preflight requests before routing, so they never need a token.
     * Credentials are allowed because the refresh token travels in a cookie; that is why origins are
     * listed as patterns instead of {@code *}, which browsers refuse together with credentials.
     */
    @Bean
    public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(
                corsProperties.allowedOriginPatterns() == null ? List.of() : corsProperties.allowedOriginPatterns()
        );
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }
}
