package team.nofantasy.recommendationsystem.apigateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayErrorHandlerTest {
    private final GatewayErrorHandler handler = new GatewayErrorHandler();

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/anything"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"status\":404,\"error\":\"Not Found\",\"message\":\"No such endpoint\"}");
    }

    @Test
    void gatewayTimeoutIsReported() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":504");
    }

    @Test
    void unexpectedExceptionAnswers500WithoutLeakingDetails() {
        MockServerWebExchange exchange = exchange();

        handler.handle(exchange, new IllegalStateException("secret internal detail")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Internal error\"}")
                .doesNotContain("secret");
    }

    @Test
    void committedResponseIsLeftAlone() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setComplete().block();
        IllegalStateException error = new IllegalStateException("late failure");

        assertThatThrownBy(() -> handler.handle(exchange, error).block()).isSameAs(error);
    }
}