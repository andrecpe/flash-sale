package br.com.flashsale.orderprocessor.infrastructure.web;

import br.com.flashsale.orderprocessor.application.ConsultarPedidoUseCase;
import br.com.flashsale.orderprocessor.domain.Pedido;
import br.com.flashsale.orderprocessor.infrastructure.correlation.CorrelationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Etapa 4 (TDD): API interna consultada pelo Checkout no cache-miss (Etapa 6). */
@WebMvcTest(PedidoInternoController.class)
class PedidoInternoControllerTest {

    private static final UUID ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarPedidoUseCase consultarPedido;

    @Test
    @DisplayName("200 com o pedido no formato do enunciado")
    void encontrado() throws Exception {
        Pedido pedido = Pedido.confirmado(ID, "PROD-001", 2, "50329291076", "Fernando Augusto",
                LocalDateTime.parse("2026-09-07T20:03:57"), LocalDateTime.parse("2026-09-07T20:04:10"), "cid-12345678");
        when(consultarPedido.executar(ID)).thenReturn(Optional.of(pedido));

        mvc.perform(get("/internal/v1/orders/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido_id").value(ID.toString()))
                .andExpect(jsonPath("$.produto_id").value("PROD-001"))
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.cliente_cpf").value("50329291076"))
                .andExpect(jsonPath("$.cliente_nome").value("Fernando Augusto"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data_hora_atualizacao").value("2026-09-07T20:04:10"))
                .andExpect(header().exists(CorrelationId.HTTP_HEADER));
    }

    @Test
    @DisplayName("200 com status CANCELLED para pedido que falhou")
    void cancelado() throws Exception {
        Pedido pedido = Pedido.cancelado(ID, "PROD-001", 2, "50329291076", "Cliente FALHA",
                LocalDateTime.parse("2026-09-07T20:03:57"), LocalDateTime.parse("2026-09-07T20:04:10"),
                "Falha no processamento do pedido", "cid-12345678");
        when(consultarPedido.executar(ID)).thenReturn(Optional.of(pedido));

        mvc.perform(get("/internal/v1/orders/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("404 com {\"mensagem\": \"Pedido não encontrado\"} quando não existe")
    void naoEncontrado() throws Exception {
        when(consultarPedido.executar(ID)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/v1/orders/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido não encontrado"));
    }

    @Test
    @DisplayName("404 quando o id não é um UUID (nenhum pedido teria esse id)")
    void idInvalido() throws Exception {
        mvc.perform(get("/internal/v1/orders/{id}", "nao-e-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido não encontrado"));

        verifyNoInteractions(consultarPedido);
    }
}

