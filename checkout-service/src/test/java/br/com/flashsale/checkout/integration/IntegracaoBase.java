package br.com.flashsale.checkout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Base dos testes de integração do Checkout: aplicação completa (HTTP real) sobre PostgreSQL, Redis e RabbitMQ
 * reais. Todas as subclasses compartilham o mesmo contexto Spring — e, portanto, os mesmos containers.
 * Cada teste usa produtos/pedidos próprios para não depender da ordem de execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainersConfig.class)
abstract class IntegracaoBase {

    static final String CPF_VALIDO = "52998224725";
    static final String FILA_PEDIDOS = "order-created";
    static final String FILA_COMPENSACAO = "order-compensation";
    static final String DLQ_COMPENSACAO = "order-compensation.dlq";
    static final String EXCHANGE = "orders.exchange";

    static final OrderProcessorStub ORDER_PROCESSOR = OrderProcessorStub.iniciar();

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("app.consulta-pedido.order-processor-url", ORDER_PROCESSOR::url);
    }

    @LocalServerPort
    int porta;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcClient jdbc;

    // ---------- HTTP ----------

    HttpResponse<String> postarPedido(String produtoId, int quantidade, String correlationId) {
        String corpo = """
                {"produto_id":"%s","quantidade":%d,"cliente_cpf":"%s","cliente_nome":"Cliente Integração"}"""
                .formatted(produtoId, quantidade, CPF_VALIDO);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/v1/orders"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (correlationId != null) {
            req.header("X-Correlation-ID", correlationId);
        }
        return enviar(req.build());
    }

    HttpResponse<String> consultarPedido(UUID pedidoId) {
        return enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/v1/orders/" + pedidoId))
                .timeout(Duration.ofSeconds(30)).GET().build());
    }

    private static HttpResponse<String> enviar(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Falha de I/O na chamada HTTP de teste", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada HTTP de teste interrompida", e);
        }
    }

    JsonNode corpoJson(HttpResponse<String> resposta) {
        try {
            return json.readTree(resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("Corpo não é JSON: " + resposta.body(), e);
        }
    }

    // ---------- Redis ----------

    void definirEstoque(String produtoId, int quantidade) {
        redis.opsForValue().set("estoque:" + produtoId, String.valueOf(quantidade));
    }

    int estoque(String produtoId) {
        String valor = redis.opsForValue().get("estoque:" + produtoId);
        return valor == null ? -1 : Integer.parseInt(valor);
    }

    // ---------- RabbitMQ ----------

    void limparFilas() {
        amqpAdmin.purgeQueue(FILA_PEDIDOS, false);
        amqpAdmin.purgeQueue(FILA_COMPENSACAO, false);
        amqpAdmin.purgeQueue(DLQ_COMPENSACAO, false);
    }

    int mensagensNaFila(String fila) {
        var info = amqpAdmin.getQueueInfo(fila);
        return info == null ? -1 : info.getMessageCount();
    }

    /** Publica uma compensação como o Order Processor faz — inclusive com o __TypeId__ de outro serviço. */
    void publicarCompensacao(String corpoJson, String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader("__TypeId__", "br.com.flashsale.orderprocessor.infrastructure.messaging.CompensacaoMessage");
        if (correlationId != null) {
            props.setHeader("x-correlation-id", correlationId);
        }
        rabbit.send(EXCHANGE, FILA_COMPENSACAO, new Message(corpoJson.getBytes(StandardCharsets.UTF_8), props));
    }

    static String compensacaoJson(UUID pedidoId, String produtoId, int quantidade) {
        return """
                {"pedido_id":"%s","produto_id":"%s","quantidade":%d,"motivo":"Falha no processamento"}"""
                .formatted(pedidoId, produtoId, quantidade);
    }
}

