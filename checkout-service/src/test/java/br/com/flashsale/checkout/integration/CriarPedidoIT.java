package br.com.flashsale.checkout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** POST /v1/orders de ponta a ponta: reserva no Redis + publicação real em order-created. */
class CriarPedidoIT extends IntegracaoBase {

    @BeforeEach
    void limpar() {
        limparFilas();
    }

    @Test
    @DisplayName("POST aceito: 202, estoque reservado e mensagem persistente em order-created com o correlation ID")
    void publicaEmOrderCreated() throws IOException {
        definirEstoque("PROD-IT-A", 5);

        HttpResponse<String> resposta = postarPedido("PROD-IT-A", 2, "it-correlation-0001");

        assertThat(resposta.statusCode()).isEqualTo(202);
        assertThat(resposta.headers().firstValue("X-Correlation-ID")).contains("it-correlation-0001");
        JsonNode corpo = corpoJson(resposta);
        assertThat(corpo.get("status").asText()).isEqualTo("PROCESSING");
        UUID pedidoId = UUID.fromString(corpo.get("pedido_id").asText());
        assertThat(estoque("PROD-IT-A")).isEqualTo(3);

        Message mensagem = rabbit.receive(FILA_PEDIDOS, 5_000);
        assertThat(mensagem).as("mensagem em order-created").isNotNull();
        JsonNode conteudo = json.readTree(mensagem.getBody());
        assertThat(conteudo.get("pedido_id").asText()).isEqualTo(pedidoId.toString());
        assertThat(conteudo.get("produto_id").asText()).isEqualTo("PROD-IT-A");
        assertThat(conteudo.get("quantidade").asInt()).isEqualTo(2);
        assertThat(conteudo.get("cliente_cpf").asText()).isEqualTo(CPF_VALIDO);
        assertThat(conteudo.get("data_hora_criacao").asText()).isNotBlank();
        assertThat(mensagem.getMessageProperties().getHeaders()).containsEntry("x-correlation-id", "it-correlation-0001");
        assertThat(mensagem.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    @DisplayName("Sem correlation ID no pedido: o Checkout gera um e propaga na resposta e na mensagem")
    void geraCorrelationIdQuandoAusente() {
        definirEstoque("PROD-IT-A2", 1);

        HttpResponse<String> resposta = postarPedido("PROD-IT-A2", 1, null);

        assertThat(resposta.statusCode()).isEqualTo(202);
        String gerado = resposta.headers().firstValue("X-Correlation-ID").orElseThrow();
        Message mensagem = rabbit.receive(FILA_PEDIDOS, 5_000);
        assertThat(mensagem).isNotNull();
        assertThat(mensagem.getMessageProperties().getHeaders()).containsEntry("x-correlation-id", gerado);
    }

    @Test
    @DisplayName("Estoque insuficiente: 409, estoque intacto e nada publicado")
    void estoqueInsuficienteRetorna409() {
        definirEstoque("PROD-IT-B", 1);

        HttpResponse<String> resposta = postarPedido("PROD-IT-B", 2, null);

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(corpoJson(resposta).get("mensagem").asText()).isNotBlank();
        assertThat(estoque("PROD-IT-B")).isEqualTo(1);
        assertThat(rabbit.receive(FILA_PEDIDOS, 500)).as("nenhuma mensagem publicada").isNull();
    }

    @Test
    @DisplayName("Produto sem estoque cadastrado: 409 (e a chave não é criada com saldo negativo)")
    void produtoInexistenteRetorna409() {
        HttpResponse<String> resposta = postarPedido("PROD-IT-NAO-EXISTE", 1, null);

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(estoque("PROD-IT-NAO-EXISTE")).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Dados inválidos: 400 sem tocar no estoque")
    void dadosInvalidosRetorna400() {
        definirEstoque("PROD-IT-C0", 3);

        HttpResponse<String> resposta = postarPedido("PROD-IT-C0", 0, null);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(estoque("PROD-IT-C0")).isEqualTo(3);
        assertThat(rabbit.receive(FILA_PEDIDOS, 500)).isNull();
    }
}

