package team.nofantasy.recommendationsystem.apigateway.error;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Turns every error raised while handling a request (no route, upstream down or too slow, a bug in a filter)
 * into the JSON body of {@link ErrorResponseWriter}. Runs before Spring Boot's default handler, which has order -1.
 * Details of unexpected errors go to the log, never to the client.
 */
@Component
@Order(-2)
@Slf4j
public class GatewayErrorHandler implements WebExceptionHandler {
    @Override
    @NullMarked
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = ex instanceof ResponseStatusException responseStatus ?
                responseStatus.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;

        if (status.is5xxServerError()) {
            log.atError()
                    .setMessage("Request failed")
                    .addKeyValue("status", status.value())
                    .addKeyValue("method", exchange.getRequest().getMethod())
                    .addKeyValue("path", exchange.getRequest().getPath().value())
                    .setCause(ex)
                    .log();
        }

        return ErrorResponseWriter.write(exchange, status, messageFor(status));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request is malformed";
            case 404 -> "No such endpoint";
            case 405 -> "The method is not allowed";
            case 502, 503 -> "The service is temporarily unavailable";
            case 504 -> "The service did not answer in time";
            default -> status.is5xxServerError() ? "Internal error" : "The request cannot be processed";
        };
    }
}