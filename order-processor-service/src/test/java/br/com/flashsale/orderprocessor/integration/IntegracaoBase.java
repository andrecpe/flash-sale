package br.com.flashsale.orderprocessor.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Base dos testes de integração do Order Processor: aplicação completa sobre PostgreSQL e RabbitMQ reais.
 * Cada teste usa pedidos próprios e limpa as filas/tabela para não depender de ordem de execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(ContainersConfig.class)
abstract class IntegracaoBase {

    static final String FILA_PEDIDOS = "order-created";
    static final String FILA_COMPENSACAO = "order-compensation";
    static final String DLQ_PEDIDOS = "order-created.dlq";
    static final String DLQ_COMPENSACAO = "order-compensation.dlq";
    static final String EXCHANGE = "orders.exchange";
    static final String CPF_VALIDO = "52998224725";
    static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void limparEstado() {
        limparFilas();
        jdbc.sql("DELETE FROM pedidos").update();
    }

    void limparFilas() {
        amqpAdmin.purgeQueue(FILA_PEDIDOS, false);
        amqpAdmin.purgeQueue(FILA_COMPENSACAO, false);
        amqpAdmin.purgeQueue(DLQ_PEDIDOS, false);
        amqpAdmin.purgeQueue(DLQ_COMPENSACAO, false);
    }

    void publicarOrderCreated(UUID pedidoId, String produtoId, int quantidade, String clienteNome,
                              String correlationId, String typeId) {
        publicarOrderCreated(pedidoId, produtoId, quantidade, CPF_VALIDO, clienteNome,
                "2026-09-07T20:03:57", correlationId, typeId);
    }

    void publicarOrderCreated(UUID pedidoId, String produtoId, int quantidade, String clienteCpf,
                              String clienteNome, String dataHoraCriacao, String correlationId,
                              String typeId) {
        String corpo = ("{\"pedido_id\":\"%s\",\"produto_id\":\"%s\",\"quantidade\":%d,"
                + "\"cliente_cpf\":\"%s\",\"cliente_nome\":\"%s\",\"data_hora_criacao\":\"%s\"}")
                .formatted(pedidoId, produtoId, quantidade, clienteCpf, clienteNome, dataHoraCriacao);
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (correlationId != null) {
            props.setHeader("x-correlation-id", correlationId);
        }
        if (typeId != null) {
            props.setHeader("__TypeId__", typeId);
        }
        rabbit.send(EXCHANGE, FILA_PEDIDOS, new Message(corpo.getBytes(StandardCharsets.UTF_8), props));
    }

    Message aguardarMensagem(String fila) {
        Instant limite = Instant.now().plus(TIMEOUT);
        Message mensagem;
        do {
            mensagem = rabbit.receive(fila, 500);
            if (mensagem != null) {
                return mensagem;
            }
        } while (Instant.now().isBefore(limite));
        throw new AssertionError("Mensagem não apareceu na fila " + fila + " dentro de " + TIMEOUT);
    }

    int mensagensNaFila(String fila) {
        var info = amqpAdmin.getQueueInfo(fila);
        return info == null ? -1 : info.getMessageCount();
    }

    void aguardarMensagensNaFila(String fila, int esperado) {
        Instant limite = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(limite)) {
            if (mensagensNaFila(fila) == esperado) {
                return;
            }
            dormir();
        }
        throw new AssertionError("Fila " + fila + " não atingiu a contagem " + esperado + " dentro de " + TIMEOUT);
    }

    int totalPedidos() {
        return jdbc.sql("SELECT COUNT(*) FROM pedidos").query(Integer.class).single();
    }

    int totalPedidos(UUID pedidoId) {
        return jdbc.sql("SELECT COUNT(*) FROM pedidos WHERE pedido_id = :id")
                .param("id", pedidoId)
                .query(Integer.class)
                .single();
    }

    PedidoRegistro aguardarPedido(UUID pedidoId) {
        Instant limite = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(limite)) {
            var registro = jdbc.sql("SELECT status, correlation_id, motivo FROM pedidos WHERE pedido_id = :id")
                    .param("id", pedidoId)
                    .query((rs, i) -> new PedidoRegistro(rs.getString("status"), rs.getString("correlation_id"),
                            rs.getString("motivo")))
                    .optional();
            if (registro.isPresent()) {
                return registro.get();
            }
            dormir();
        }
        throw new AssertionError("Pedido " + pedidoId + " não foi persistido dentro de " + TIMEOUT);
    }

    JsonNode corpoJson(Message mensagem) {
        try {
            return json.readTree(mensagem.getBody());
        } catch (IOException e) {
            throw new IllegalStateException("Mensagem não é JSON: " + new String(mensagem.getBody(), StandardCharsets.UTF_8), e);
        }
    }

    private static void dormir() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrompida", e);
        }
    }

    record PedidoRegistro(String status, String correlationId, String motivo) {
    }
}


