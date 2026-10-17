package br.com.flashsale.checkout.infrastructure.correlation;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Saída HTTP (RestClient do Checkout → Order Processor): propaga o X-Correlation-ID da transação atual.
 */
public class CorrelationIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CorrelationId.current().ifPresent(id -> request.getHeaders().set(CorrelationId.HTTP_HEADER, id));
        return execution.execute(request, body);
    }
}

