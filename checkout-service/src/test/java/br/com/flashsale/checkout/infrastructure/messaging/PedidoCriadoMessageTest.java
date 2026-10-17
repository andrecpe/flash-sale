package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.domain.Pedido;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoCriadoMessageTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:03:57Z"), ZoneId.of("America/Sao_Paulo"));
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("Converte o pedido para o contrato da fila order-created (snake_case, data ISO sem fuso)")
    void contratoDaMensagem() throws Exception {
        Pedido pedido = Pedido.criar("PROD-001", 2, "50329291076", "Fernando Augusto", clock);

        String corpo = json.writeValueAsString(PedidoCriadoMessage.de(pedido));

        assertThat(json.readTree(corpo)).isEqualTo(json.readTree("""
                {
                  "pedido_id": "%s",
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "cliente_cpf": "50329291076",
                  "cliente_nome": "Fernando Augusto",
                  "data_hora_criacao": "2026-09-07T20:03:57"
                }
                """.formatted(pedido.id())));
    }
}

