package br.com.flashsale.gateway.correlation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();
    private final AtomicReference<ServerWebExchange> repassadoAoUpstream = new AtomicReference<>();
    private final WebFilterChain cadeia = exchange -> {
        repassadoAoUpstream.set(exchange);
        return exchange.getResponse().setComplete();
    };

    private MockServerWebExchange executar(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, cadeia).block();
        return exchange;
    }

    private String headerRepassado() {
        return repassadoAoUpstream.get().getRequest().getHeaders().getFirst(CorrelationId.HTTP_HEADER);
    }

    @Test
    @DisplayName("Sem header: gera UUID, repassa ao upstream e devolve na resposta")
    void semHeaderGeraRepassaEDevolve() {
        MockServerWebExchange exchange = executar(MockServerHttpRequest.post("/v1/orders").build());

        String gerado = headerRepassado();
        assertThat(CorrelationId.isValid(gerado)).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HTTP_HEADER)).isEqualTo(gerado);
    }

    @Test
    @DisplayName("Header válido: mantém o mesmo valor no upstream e na resposta")
    void headerValidoEhMantido() {
        MockServerWebExchange exchange = executar(MockServerHttpRequest.post("/v1/orders")
                .header(CorrelationId.HTTP_HEADER, "cliente-app-12345678").build());

        assertThat(headerRepassado()).isEqualTo("cliente-app-12345678");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HTTP_HEADER)).isEqualTo("cliente-app-12345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "id com espaco", "id!invalido", "abc12345\r\nINJETADO",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("Header inválido (curto, > 64, caracteres proibidos, CRLF): descarta e gera novo")
    void headerInvalidoEhSubstituido(String invalido) {
        MockServerWebExchange exchange = executar(MockServerHttpRequest.post("/v1/orders")
                .header(CorrelationId.HTTP_HEADER, invalido).build());

        assertThat(headerRepassado()).isNotEqualTo(invalido);
        assertThat(CorrelationId.isValid(headerRepassado())).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HTTP_HEADER)).isEqualTo(headerRepassado());
    }

    @Test
    @DisplayName("Resposta contém um único X-Correlation-ID mesmo se o upstream também devolver o header")
    void respostaComHeaderUnico() {
        WebFilterChain upstreamQueEcoaOHeader = exchange -> {
            repassadoAoUpstream.set(exchange);
            exchange.getResponse().getHeaders().add(CorrelationId.HTTP_HEADER,
                    exchange.getRequest().getHeaders().getFirst(CorrelationId.HTTP_HEADER));
            return exchange.getResponse().setComplete();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/orders/1")
                .header(CorrelationId.HTTP_HEADER, "cliente-app-12345678").build());

        filter.filter(exchange, upstreamQueEcoaOHeader).block();

        assertThat(exchange.getResponse().getHeaders().get(CorrelationId.HTTP_HEADER))
                .containsExactly("cliente-app-12345678");
    }
}

