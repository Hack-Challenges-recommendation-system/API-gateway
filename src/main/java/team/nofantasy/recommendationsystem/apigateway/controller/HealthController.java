package team.nofantasy.recommendationsystem.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Health of the gateway itself. The services' own {@code /api/v1/health} isn't routed:
 * every service has that path, and compose checks them directly.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public Mono<Void> getHealth() {
        return Mono.empty();
    }
}
