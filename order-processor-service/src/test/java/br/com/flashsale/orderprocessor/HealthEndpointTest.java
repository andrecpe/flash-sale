package br.com.flashsale.orderprocessor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;


import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Etapa 1 (TDD): o serviço deve expor health check para o Docker (liveness/readiness)
 * e não deve expor endpoints sensíveis do Actuator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthEndpointTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("GET /actuator/health retorna 200 e status UP")
    void healthDeveEstarUp() {
        ResponseEntity<Map> resposta = rest.getForEntity("/actuator/health", Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("GET /actuator/health/liveness retorna 200 e status UP")
    void livenessDeveEstarUp() {
        ResponseEntity<Map> resposta = rest.getForEntity("/actuator/health/liveness", Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("GET /actuator/health/readiness retorna 200 e status UP")
    void readinessDeveEstarUp() {
        ResponseEntity<Map> resposta = rest.getForEntity("/actuator/health/readiness", Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("status", "UP");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("GET /actuator/info retorna 200 com o nome do serviço")
    void infoDeveIdentificarOServico() {
        ResponseEntity<Map> resposta = rest.getForEntity("/actuator/info", Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> app = (Map<String, Object>) resposta.getBody().get("app");
        assertThat(app).containsEntry("name", "order-processor-service");
    }

    @Test
    @DisplayName("Endpoints sensíveis do Actuator (env) não são expostos")
    void envNaoDeveSerExposto() {
        ResponseEntity<String> resposta = rest.getForEntity("/actuator/env", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

