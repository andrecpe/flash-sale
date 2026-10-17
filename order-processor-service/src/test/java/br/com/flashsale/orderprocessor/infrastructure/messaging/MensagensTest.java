package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.Compensacao;
import br.com.flashsale.orderprocessor.application.ProcessarPedidoCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 4 (TDD): contratos das mensagens (formato do enunciado). */
class MensagensTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("Lê a mensagem de order-created do enunciado e gera o comando com o correlationId")
    void lePedidoCriado() throws Exception {
        String json = """
                {
                  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "cliente_cpf": "50329291076",
                  "cliente_nome": "Fernando Augusto",
                  "data_hora_criacao": "2026-09-07T20:03:57"
                }
                """;

        PedidoCriadoMessage mensagem = mapper.readValue(json, PedidoCriadoMessage.class);

        assertThat(mensagem.paraComando("cid-12345678")).isEqualTo(new ProcessarPedidoCommand(
                UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402"), "PROD-001", 2,
                "50329291076", "Fernando Augusto", LocalDateTime.parse("2026-09-07T20:03:57"), "cid-12345678"));
        assertThat(mensagem.toString()).doesNotContain("50329291076", "Fernando");
    }

    @Test
    @DisplayName("Campos extras na mensagem são ignorados (evolução do contrato)")
    void ignoraCamposExtras() throws Exception {
        String json = """
                {"pedido_id":"b819cc65-f6f0-478c-8bb7-69ee1c4f6402","produto_id":"PROD-001","quantidade":1,
                 "cliente_cpf":"50329291076","data_hora_criacao":"2026-09-07T20:03:57","novo_campo":"x"}
                """;

        assertThat(mapper.readValue(json, PedidoCriadoMessage.class).quantidade()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mensagem de order-compensation idêntica ao enunciado")
    void escreveCompensacao() throws Exception {
        Compensacao compensacao = new Compensacao(UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402"),
                "PROD-001", 2, "Falha no processamento do pedido");

        String json = mapper.writeValueAsString(CompensacaoMessage.de(compensacao));

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree("""
                {
                  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "motivo": "Falha no processamento do pedido"
                }
                """));
    }
}

