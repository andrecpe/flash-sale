package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.ConsultarPedidoUseCase;
import br.com.flashsale.checkout.application.CriarPedidoCommand;
import br.com.flashsale.checkout.application.CriarPedidoUseCase;
import br.com.flashsale.checkout.application.EstoqueInsuficienteException;
import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.application.PedidoNaoEncontradoException;
import br.com.flashsale.checkout.application.PublicacaoIndisponivelException;
import br.com.flashsale.checkout.application.ServicoIndisponivelException;
import br.com.flashsale.checkout.domain.Pedido;
import br.com.flashsale.checkout.domain.PedidoInvalidoException;
import br.com.flashsale.checkout.domain.StatusPedido;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    private static final String CORPO = """
            {
              "produto_id": "PROD-001",
              "quantidade": 2,
              "cliente_cpf": "50329291076",
              "cliente_nome": "Fernando Augusto"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CriarPedidoUseCase criarPedido;
    @MockitoBean
    private ConsultarPedidoUseCase consultarPedido;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:03:57Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    @DisplayName("202 Accepted com pedido_id, status PROCESSING e data_hora_requisicao")
    void pedidoAceito() throws Exception {
        Pedido pedido = Pedido.criar("PROD-001", 2, "50329291076", "Fernando Augusto", clock);
        when(criarPedido.executar(any())).thenReturn(pedido);

        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pedido_id").value(pedido.id().toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data_hora_requisicao").value("2026-09-07T20:03:57"))
                .andExpect(header().exists(CorrelationId.HTTP_HEADER));

        verify(criarPedido).executar(new CriarPedidoCommand("PROD-001", 2, "50329291076", "Fernando Augusto"));
    }

    @Test
    @DisplayName("409 Conflict quando não há estoque suficiente")
    void estoqueInsuficiente() throws Exception {
        when(criarPedido.executar(any())).thenThrow(new EstoqueInsuficienteException("PROD-001"));

        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Estoque insuficiente para o produto PROD-001"));
    }

    @Test
    @DisplayName("400 Bad Request com a lista de erros de validação")
    void dadosInvalidos() throws Exception {
        when(criarPedido.executar(any())).thenThrow(new PedidoInvalidoException(
                List.of("produto_id é obrigatório", "quantidade deve ser maior que zero")));

        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados do pedido inválidos"))
                .andExpect(jsonPath("$.erros", containsInAnyOrder(
                        "produto_id é obrigatório", "quantidade deve ser maior que zero")));
    }

    @Test
    @DisplayName("400 Bad Request para JSON mal formado")
    void jsonMalFormado() throws Exception {
        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{ produto_id: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisição inválido"));
    }

    @Test
    @DisplayName("400 Bad Request quando quantidade não é numérica")
    void quantidadeNaoNumerica() throws Exception {
        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produto_id\":\"PROD-001\",\"quantidade\":\"dois\",\"cliente_cpf\":\"50329291076\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("503 Service Unavailable quando o broker não confirma a publicação")
    void brokerIndisponivel() throws Exception {
        when(criarPedido.executar(any())).thenThrow(new PublicacaoIndisponivelException("broker fora", null));

        mvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Serviço temporariamente indisponível. Tente novamente."));
    }

    // ---------------------------------------------------------------------------------------------
    // Etapa 6: GET /v1/orders/{pedido_id}
    // ---------------------------------------------------------------------------------------------

    private static final UUID ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");

    @Test
    @DisplayName("GET 200 com o corpo do enunciado (snake_case, data sem fuso)")
    void consultaEncontrada() throws Exception {
        when(consultarPedido.executar(ID)).thenReturn(new PedidoDetalhe(ID, "PROD-001", 2, "50329291076",
                "Fernando Augusto", StatusPedido.CONFIRMED, LocalDateTime.of(2026, 9, 7, 20, 4, 10)));

        mvc.perform(get("/v1/orders/{id}", ID))
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
    @DisplayName("GET 404 com {\"mensagem\": \"Pedido não encontrado\"}")
    void consultaNaoEncontrada() throws Exception {
        when(consultarPedido.executar(ID)).thenThrow(new PedidoNaoEncontradoException());

        mvc.perform(get("/v1/orders/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido não encontrado"));
    }

    @Test
    @DisplayName("GET com id que não é UUID → 404 sem consultar (não existe pedido com esse id)")
    void consultaIdInvalido() throws Exception {
        mvc.perform(get("/v1/orders/{id}", "123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido não encontrado"));

        verifyNoInteractions(consultarPedido);
    }

    @Test
    @DisplayName("GET 503 quando o Order Processor está indisponível (circuito aberto/timeout)")
    void consultaIndisponivel() throws Exception {
        when(consultarPedido.executar(ID)).thenThrow(new ServicoIndisponivelException("circuito aberto", null));

        mvc.perform(get("/v1/orders/{id}", ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Serviço temporariamente indisponível. Tente novamente."));
    }
}

