package br.com.flashsale.orderprocessor.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Integração do consumo de {@code order-created}: PostgreSQL real, RabbitMQ real, DLQ e correlation ID. */
class PedidoCriadoIT extends IntegracaoBase {

    @Test
    @DisplayName("Pedido confirmado: persiste no PostgreSQL real e ignora __TypeId__ de outro serviço")
    void confirmaPedido() {
        UUID pedidoId = UUID.randomUUID();
        publicarOrderCreated(pedidoId, "PROD-IT-OP-A", 2, "Cliente Integração", "it-order-0001",
                "br.com.flashsale.checkout.infrastructure.messaging.CompensacaoMessage");

        PedidoRegistro pedido = aguardarPedido(pedidoId);

        assertThat(pedido.status()).isEqualTo("CONFIRMED");
        assertThat(pedido.correlationId()).isEqualTo("it-order-0001");
        assertThat(pedido.motivo()).isNull();
        assertThat(totalPedidos(pedidoId)).isEqualTo(1);
        assertThat(mensagensNaFila(FILA_COMPENSACAO)).isZero();
        assertThat(mensagensNaFila(DLQ_PEDIDOS)).isZero();
    }

    @Test
    @DisplayName("Pedido com falha: cancela, publica compensação e propaga x-correlation-id de ponta a ponta")
    void compensaPedidoEPropagaCorrelationId() {
        UUID pedidoId = UUID.randomUUID();
        publicarOrderCreated(pedidoId, "PROD-IT-OP-B", 3, "Cliente FALHA Integração", "it-order-0002",
                "br.com.flashsale.checkout.infrastructure.messaging.CompensacaoMessage");

        PedidoRegistro pedido = aguardarPedido(pedidoId);
        var compensacao = aguardarMensagem(FILA_COMPENSACAO);

        assertThat(pedido.status()).isEqualTo("CANCELLED");
        assertThat(pedido.correlationId()).isEqualTo("it-order-0002");
        assertThat(pedido.motivo()).isEqualTo("Falha no processamento do pedido");
        assertThat(corpoJson(compensacao).get("pedido_id").asText()).isEqualTo(pedidoId.toString());
        assertThat(corpoJson(compensacao).get("produto_id").asText()).isEqualTo("PROD-IT-OP-B");
        assertThat(corpoJson(compensacao).get("quantidade").asInt()).isEqualTo(3);
        assertThat(corpoJson(compensacao).get("motivo").asText()).isEqualTo("Falha no processamento do pedido");
        assertThat(compensacao.getMessageProperties().getHeaders()).containsEntry("x-correlation-id", "it-order-0002");
        assertThat(totalPedidos(pedidoId)).isEqualTo(1);
        assertThat(mensagensNaFila(DLQ_PEDIDOS)).isZero();
    }

    @Test
    @DisplayName("Mensagem duplicada usa ON CONFLICT DO NOTHING: apenas 1 pedido persiste")
    void mensagemDuplicadaNaoDuplicaNoBanco() throws InterruptedException {
        UUID pedidoId = UUID.randomUUID();
        publicarOrderCreated(pedidoId, "PROD-IT-OP-C", 1, "Cliente Integração", "it-order-0003",
                "br.com.flashsale.checkout.infrastructure.messaging.CompensacaoMessage");

        aguardarPedido(pedidoId);
        publicarOrderCreated(pedidoId, "PROD-IT-OP-C", 1, "Cliente Integração", "it-order-0003",
                "br.com.flashsale.checkout.infrastructure.messaging.CompensacaoMessage");

        aguardarMensagensNaFila(FILA_PEDIDOS, 0);
        Thread.sleep(500);
        PedidoRegistro pedido = aguardarPedido(pedidoId);

        assertThat(pedido.status()).isEqualTo("CONFIRMED");
        assertThat(totalPedidos(pedidoId)).isEqualTo(1);
        assertThat(mensagensNaFila(FILA_COMPENSACAO)).isZero();
    }

    @Test
    @DisplayName("JSON inválido vai para a DLQ sem persistir pedido")
    void mensagemInvalidaVaiParaDlq() {
        rabbit.send(EXCHANGE, FILA_PEDIDOS, new org.springframework.amqp.core.Message(
                "{ quebrado".getBytes(java.nio.charset.StandardCharsets.UTF_8), new org.springframework.amqp.core.MessageProperties()));

        aguardarMensagensNaFila(DLQ_PEDIDOS, 1);
        assertThat(mensagensNaFila(DLQ_PEDIDOS)).isEqualTo(1);
        assertThat(totalPedidos()).isZero();
        assertThat(mensagensNaFila(FILA_COMPENSACAO)).isZero();
    }
}

