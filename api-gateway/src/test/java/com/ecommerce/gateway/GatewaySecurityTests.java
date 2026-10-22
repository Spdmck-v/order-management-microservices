package com.ecommerce.gateway;

import com.ecommerce.gateway.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.HttpURLConnection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public behaviour of the unauthenticated gateway surface: every /api/**
 * request without a valid bearer token must answer 401 INVALID_TOKEN in the
 * unique error format and echo the X-Trace-Id response header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTests {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldReturn401InvalidTokenWithTraceIdWhenNoToken() {
        EntityExchangeResult<ErrorResponse> result = webTestClient.get().uri("/api/inventory/products")
                .exchange()
                .expectStatus().isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
                .expectHeader().exists("X-Trace-Id")
                .expectBody(ErrorResponse.class)
                .returnResult();

        ErrorResponse body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.code()).isEqualTo("INVALID_TOKEN");
        assertThat(body.message()).isNotBlank();
        assertThat(body.timestamp()).isNotBlank();
        assertThat(body.traceId()).isNotBlank()
                .isEqualTo(result.getResponseHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void shouldReturn401ForOrdersRouteWithoutToken() {
        webTestClient.get().uri("/api/orders/some-id")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void shouldAllowActuatorHealthWithoutToken() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
