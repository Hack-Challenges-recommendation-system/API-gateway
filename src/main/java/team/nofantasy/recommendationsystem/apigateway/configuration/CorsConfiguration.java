package team.nofantasy.recommendationsystem.apigateway.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.CorsConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Configuration
public class CorsConfiguration {
    @Bean
    public CorsWebFilter corsWebFilter(CorsConfigurationProperties corsConfigurationProperties) {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOriginPatterns(
                corsConfigurationProperties.allowedOriginPatterns() == null ?
                        List.of() : corsConfigurationProperties.allowedOriginPatterns()
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
