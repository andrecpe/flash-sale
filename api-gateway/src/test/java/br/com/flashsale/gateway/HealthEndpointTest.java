package br.com.flashsale.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Etapa 1 (TDD): o gateway deve expor health check para o Docker (liveness/readiness)
 * e não deve expor endpoints sensíveis do Actuator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class HealthEndpointTest {

    @Autowired
    private WebTestClient web;

    @Test
    @DisplayName("GET /actuator/health retorna 200 e status UP")
    void healthDeveEstarUp() {
        web.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("GET /actuator/health/liveness retorna 200 e status UP")
    void livenessDeveEstarUp() {
        web.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("GET /actuator/health/readiness retorna 200 e status UP")
    void readinessDeveEstarUp() {
        web.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("GET /actuator/info retorna 200 com o nome do serviço")
    void infoDeveIdentificarOServico() {
        web.get().uri("/actuator/info").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.app.name").isEqualTo("api-gateway");
    }

    @Test
    @DisplayName("Endpoints sensíveis do Actuator (env e gateway) não são expostos")
    void endpointsSensiveisNaoDevemSerExpostos() {
        web.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        web.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    }
}

