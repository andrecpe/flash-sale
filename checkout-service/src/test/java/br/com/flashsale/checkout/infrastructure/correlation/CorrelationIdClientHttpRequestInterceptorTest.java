package br.com.flashsale.checkout.infrastructure.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdClientHttpRequestInterceptorTest {

    private final CorrelationIdClientHttpRequestInterceptor interceptor = new CorrelationIdClientHttpRequestInterceptor();
    private final AtomicReference<HttpHeaders> headersEnviados = new AtomicReference<>();
    private final ClientHttpRequestExecution execucao = (request, body) -> {
        headersEnviados.set(request.getHeaders());
        return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    };

    @AfterEach
    void limparMdc() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    @DisplayName("Chamada HTTP de saída leva o X-Correlation-ID do contexto atual")
    void propagaHeaderDoContexto() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "pedido-teste-12345678");
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://order-processor-service:8082/internal/v1/orders/1"));

        interceptor.intercept(request, new byte[0], execucao);

        assertThat(headersEnviados.get().getFirst(CorrelationId.HTTP_HEADER)).isEqualTo("pedido-teste-12345678");
    }

    @Test
    @DisplayName("Sem contexto, a chamada segue sem o header")
    void semContextoNaoAdicionaHeader() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://order-processor-service:8082/internal/v1/orders/1"));

        interceptor.intercept(request, new byte[0], execucao);

        assertThat(headersEnviados.get().containsKey(CorrelationId.HTTP_HEADER)).isFalse();
    }
}

