package br.com.flashsale.checkout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** GET /v1/orders/{id}: cache-aside no Redis real, API interna simulada, 3 tentativas antes do 404. */
class ConsultaPedidoIT extends IntegracaoBase {

    private static String pedidoJson(UUID id, String status) {
        return """
                {"pedido_id":"%s","produto_id":"PROD-IT-GET","quantidade":2,"cliente_cpf":"%s",
                 "cliente_nome":"Cliente Integração","status":"%s","data_hora_atualizacao":"2026-01-01T10:00:00"}"""
                .formatted(id, CPF_VALIDO, status);
    }

    @Test
    @DisplayName("Cache-aside: 1º GET busca no Order Processor e grava no Redis (TTL); 2º GET vem do cache")
    void cacheAside() {
        UUID id = UUID.randomUUID();
        ORDER_PROCESSOR.responder(id, pedidoJson(id, "CONFIRMED"));

        HttpResponse<String> primeira = consultarPedido(id);
        assertThat(primeira.statusCode()).isEqualTo(200);
        JsonNode corpo = corpoJson(primeira);
        assertThat(corpo.get("pedido_id").asText()).isEqualTo(id.toString());
        assertThat(corpo.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(ORDER_PROCESSOR.chamadas(id)).isEqualTo(1);
        assertThat(redis.hasKey("pedido:" + id)).isTrue();
        assertThat(redis.getExpire("pedido:" + id)).isBetween(1L, 300L);

        HttpResponse<String> segunda = consultarPedido(id);
        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(corpoJson(segunda).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(ORDER_PROCESSOR.chamadas(id)).as("2º GET não consulta o Order Processor").isEqualTo(1);
    }

    @Test
    @DisplayName("Pedido inexistente: 3 tentativas no Order Processor antes do 404")
    void tresTentativasAntesDo404() {
        UUID id = UUID.randomUUID();

        HttpResponse<String> resposta = consultarPedido(id);

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertThat(ORDER_PROCESSOR.chamadas(id)).isEqualTo(3);
        assertThat(redis.hasKey("pedido:" + id)).as("404 não é cacheado").isFalse();
    }

    @Test
    @DisplayName("Pedido ainda na fila: aparece na 2ª/3ª tentativa, sem 404 indevido")
    void encontraNaTentativaSeguinte() {
        UUID id = UUID.randomUUID();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300); // o Order Processor "termina de processar" durante as tentativas
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ORDER_PROCESSOR.responder(id, pedidoJson(id, "CONFIRMED"));
        });

        HttpResponse<String> resposta = consultarPedido(id);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(ORDER_PROCESSOR.chamadas(id)).isBetween(2, 3);
    }

    @Test
    @DisplayName("Compensação invalida o cache: o próximo GET reflete o status CANCELLED")
    void compensacaoInvalidaCache() {
        UUID id = UUID.randomUUID();
        ORDER_PROCESSOR.responder(id, pedidoJson(id, "PROCESSING"));
        assertThat(corpoJson(consultarPedido(id)).get("status").asText()).isEqualTo("PROCESSING");
        assertThat(redis.hasKey("pedido:" + id)).isTrue();

        ORDER_PROCESSOR.responder(id, pedidoJson(id, "CANCELLED"));
        definirEstoque("PROD-IT-GET", 0);
        publicarCompensacao(compensacaoJson(id, "PROD-IT-GET", 2), null);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(redis.hasKey("pedido:" + id)).isFalse());
        assertThat(corpoJson(consultarPedido(id)).get("status").asText()).isEqualTo("CANCELLED");
    }
}

