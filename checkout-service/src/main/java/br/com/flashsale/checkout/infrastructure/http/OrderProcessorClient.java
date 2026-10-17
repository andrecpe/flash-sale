package br.com.flashsale.checkout.infrastructure.http;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.application.ServicoIndisponivelException;
import br.com.flashsale.checkout.application.port.PedidoConsultaPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador HTTP da API interna do Order Processor ({@code GET /internal/v1/orders/{id}}).
 * <ul>
 *   <li>{@code RestClient} com timeouts curtos e o interceptor que propaga o {@code X-Correlation-ID};</li>
 *   <li>circuit breaker (Resilience4j): erros de rede, timeouts e 5xx contam como falha; acima do limite o
 *       circuito abre e as chamadas falham na hora, sem esperar timeout — protege o Checkout no pico;</li>
 *   <li>404 é resposta de negócio (pedido ainda não processado): retorna vazio e não conta como falha.</li>
 * </ul>
 */
public class OrderProcessorClient implements PedidoConsultaPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public OrderProcessorClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<PedidoDetalhe> buscar(UUID pedidoId) {
        try {
            return circuitBreaker.executeSupplier(() -> chamar(pedidoId));
        } catch (CallNotPermittedException e) {
            throw new ServicoIndisponivelException("Order Processor indisponível (circuito aberto)", e);
        } catch (RuntimeException e) {
            throw new ServicoIndisponivelException("Falha ao consultar o Order Processor: " + e.getMessage(), e);
        }
    }

    private Optional<PedidoDetalhe> chamar(UUID pedidoId) {
        return restClient.get()
                .uri("/internal/v1/orders/{id}", pedidoId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return Optional.<PedidoDetalhe>empty();
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Order Processor respondeu " + response.getStatusCode());
                    }
                    PedidoInternoResponse corpo = response.bodyTo(PedidoInternoResponse.class);
                    if (corpo == null) {
                        throw new IllegalStateException("Order Processor respondeu sem corpo");
                    }
                    return Optional.of(corpo.paraDetalhe());
                });
    }
}

