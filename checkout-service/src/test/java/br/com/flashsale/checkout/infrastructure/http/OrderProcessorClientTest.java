package br.com.flashsale.checkout.infrastructure.http;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.application.ServicoIndisponivelException;
import br.com.flashsale.checkout.domain.StatusPedido;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationId;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationIdClientHttpRequestInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Etapa 6 (TDD): cliente HTTP da API interna do Order Processor (servidor HTTP simulado,
 * circuit breaker real do Resilience4j).
 */
class OrderProcessorClientTest {

    private static final UUID ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final String URL = "http://order-processor/internal/v1/orders/" + ID;
    private static final String CORPO = """
            {"pedido_id":"b819cc65-f6f0-478c-8bb7-69ee1c4f6402","produto_id":"PROD-001","quantidade":2,
             "cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto","status":"CANCELLED",
             "data_hora_atualizacao":"2026-09-07T20:04:10"}
            """;

    private MockRestServiceServer servidor;
    private CircuitBreaker circuito;
    private OrderProcessorClient cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://order-processor")
                .requestInterceptor(new CorrelationIdClientHttpRequestInterceptor());
        servidor = MockRestServiceServer.bindTo(builder).build();
        circuito = CircuitBreaker.of("order-processor", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        cliente = new OrderProcessorClient(builder.build(), circuito);
    }

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("200: converte o corpo do enunciado e propaga o X-Correlation-ID")
    void encontrado() {
        MDC.put(CorrelationId.MDC_KEY, "demo-etapa6-0001");
        servidor.expect(requestTo(URL)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(CorrelationId.HTTP_HEADER, "demo-etapa6-0001"))
                .andRespond(withSuccess(CORPO, MediaType.APPLICATION_JSON));

        assertThat(cliente.buscar(ID)).contains(new PedidoDetalhe(ID, "PROD-001", 2, "50329291076",
                "Fernando Augusto", StatusPedido.CANCELLED, LocalDateTime.of(2026, 9, 7, 20, 4, 10)));
        servidor.verify();
    }

    @Test
    @DisplayName("404: vazio — resposta de negócio, não conta como falha no circuit breaker")
    void naoEncontrado() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"mensagem\":\"Pedido não encontrado\"}"));

        assertThat(cliente.buscar(ID)).isEmpty();
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("500: ServicoIndisponivelException (falha registrada no circuit breaker)")
    void erroServidor() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> cliente.buscar(ID)).isInstanceOf(ServicoIndisponivelException.class);
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Falha de rede/timeout: ServicoIndisponivelException")
    void falhaDeRede() {
        servidor.expect(requestTo(URL)).andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> cliente.buscar(ID)).isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    @DisplayName("Circuito aberto: falha rápida sem chamar o Order Processor")
    void circuitoAberto() {
        circuito.transitionToOpenState();

        assertThatThrownBy(() -> cliente.buscar(ID))
                .isInstanceOf(ServicoIndisponivelException.class)
                .hasMessageContaining("circuito aberto");
        servidor.verify(); // nenhuma requisição esperada → nenhuma feita
    }

    @Test
    @DisplayName("Falhas repetidas abrem o circuito")
    void falhasAbremOCircuito() {
        servidor.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cliente.buscar(ID)).isInstanceOf(ServicoIndisponivelException.class);
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}

