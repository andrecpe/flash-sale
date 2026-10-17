package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.application.CompensarEstoqueCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 5 (TDD): leitura da mensagem de order-compensation pelo contrato do enunciado. */
class CompensacaoMessageTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("Lê o payload do enunciado (snake_case) e ignora campos extras")
    void leContratoDoEnunciado() throws Exception {
        String json = """
                {
                  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "motivo": "Falha no processamento do pedido",
                  "campo_novo": "ignorado"
                }
                """;

        CompensacaoMessage msg = mapper.readValue(json, CompensacaoMessage.class);
        CompensarEstoqueCommand comando = msg.paraComando("demo-etapa5-0001");

        assertThat(comando.pedidoId()).isEqualTo(UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402"));
        assertThat(comando.produtoId()).isEqualTo("PROD-001");
        assertThat(comando.quantidade()).isEqualTo(2);
        assertThat(comando.motivo()).isEqualTo("Falha no processamento do pedido");
        assertThat(comando.correlationId()).isEqualTo("demo-etapa5-0001");
    }

    @Test
    @DisplayName("Campos ausentes chegam nulos (a validação fica no caso de uso)")
    void camposAusentes() throws Exception {
        CompensacaoMessage msg = mapper.readValue("{\"pedido_id\":\"b819cc65-f6f0-478c-8bb7-69ee1c4f6402\"}",
                CompensacaoMessage.class);

        assertThat(msg.produtoId()).isNull();
        assertThat(msg.quantidade()).isNull();
    }
}

