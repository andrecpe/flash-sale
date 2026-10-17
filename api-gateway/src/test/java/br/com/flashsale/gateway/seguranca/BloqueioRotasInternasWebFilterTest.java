package br.com.flashsale.gateway.seguranca;

import br.com.flashsale.gateway.correlation.CorrelationIdWebFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 7 (TDD): a API interna (/internal/**) nunca é acessível pela borda. */
class BloqueioRotasInternasWebFilterTest {

    private final BloqueioRotasInternasWebFilter filtro = new BloqueioRotasInternasWebFilter();
    private final AtomicBoolean repassado = new AtomicBoolean(false);
    private final WebFilterChain cadeia = exchange -> {
        repassado.set(true);
        return exchange.getResponse().setComplete();
    };

    private MockServerWebExchange executar(String caminho) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(caminho).build());
        filtro.filter(exchange, cadeia).block();
        return exchange;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/orders/b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
            "/internal",
            "/INTERNAL/v1/orders/1",
            "/v1/orders/../internal/v1/orders/1",
            "/v1/orders/%2e%2e/internal/v1/orders/1",
            "http://localhost//internal/v1/orders/1"})
    @DisplayName("/internal/** (inclusive variações de caixa, '..' e barras duplas) → 404 sem repassar")
    void bloqueiaInterna(String caminho) {
        MockServerWebExchange exchange = executar(caminho);

        assertThat(repassado).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"mensagem\":\"Recurso não encontrado\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/orders/b819cc65-f6f0-478c-8bb7-69ee1c4f6402", "/v1/products/PROD-001/stock",
            "/actuator/health", "/v1/products/internal-promo/stock"})
    @DisplayName("Rotas públicas seguem normalmente")
    void permitePublicas(String caminho) {
        MockServerWebExchange exchange = executar(caminho);

        assertThat(repassado).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Roda logo após o filtro de correlação (o 404 também leva X-Correlation-ID e access log)")
    void ordem() {
        assertThat(filtro.getOrder()).isGreaterThan(new CorrelationIdWebFilter().getOrder());
    }
}



