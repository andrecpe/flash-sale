package br.com.flashsale.checkout.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Compensação (SAGA): mensagem em order-compensation → estoque devolvido uma única vez, registro no PostgreSQL
 * (ON CONFLICT DO NOTHING), cache invalidado; mensagem inválida vai para a DLQ.
 * As mensagens carregam o {@code __TypeId__} do Order Processor — o Checkout deve processá-las mesmo assim.
 */
class CompensacaoIT extends IntegracaoBase {

    @BeforeEach
    void limpar() {
        limparFilas();
    }

    @Test
    @DisplayName("Compensação devolve o estoque, registra no banco e invalida o cache; repetição é idempotente")
    void compensaEstoqueDeFormaIdempotente() {
        String produto = "PROD-IT-COMP";
        definirEstoque(produto, 8); // 10 reservados - 2 deste pedido já descontados
        UUID pedidoId = UUID.randomUUID();
        redis.opsForValue().set("pedido:" + pedidoId, "{\"valor\":\"antigo\"}");

        publicarCompensacao(compensacaoJson(pedidoId, produto, 2), "it-compensacao-0001");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(estoque(produto)).isEqualTo(10));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(registros(pedidoId)).isEqualTo(1));
        assertThat(jdbc.sql("SELECT correlation_id FROM compensacoes WHERE pedido_id = ?")
                .param(pedidoId).query(String.class).single()).isEqualTo("it-compensacao-0001");
        assertThat(redis.hasKey("pedido:" + pedidoId)).as("cache invalidado").isFalse();

        // reentrega (redrive da DLQ / at-least-once): não devolve de novo
        publicarCompensacao(compensacaoJson(pedidoId, produto, 2), "it-compensacao-0001");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mensagensNaFila(FILA_COMPENSACAO)).isZero());
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(estoque(produto)).isEqualTo(10));
        assertThat(registros(pedidoId)).isEqualTo(1);
        assertThat(mensagensNaFila(DLQ_COMPENSACAO)).isZero();
    }

    @Test
    @DisplayName("Mensagem inválida vai para a DLQ, sem reentrega em loop e sem alterar o estoque")
    void mensagemInvalidaVaiParaDlq() {
        String produto = "PROD-IT-DLQ";
        definirEstoque(produto, 4);

        publicarCompensacao("{\"pedido_id\":\"%s\",\"quantidade\":2}".formatted(UUID.randomUUID()), null); // sem produto_id

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mensagensNaFila(DLQ_COMPENSACAO)).isEqualTo(1));
        assertThat(mensagensNaFila(FILA_COMPENSACAO)).isZero();
        assertThat(estoque(produto)).isEqualTo(4);
    }

    private int registros(UUID pedidoId) {
        return jdbc.sql("SELECT COUNT(*) FROM compensacoes WHERE pedido_id = ?")
                .param(pedidoId).query(Integer.class).single();
    }
}

