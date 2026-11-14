package team.nofantasy.recommendationsystem.apigateway.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.CorsConfigurationProperties;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.JwtSecretConfigurationProperties;
import team.nofantasy.recommendationsystem.apigateway.configuration.properties.PublicRoutesConfigurationProperties;

@Configuration
@EnableConfigurationProperties(
        value = {
                CorsConfigurationProperties.class,
                JwtSecretConfigurationProperties.class,
                PublicRoutesConfigurationProperties.class
        }
)
public class ApiGatewayConfiguration {
}
