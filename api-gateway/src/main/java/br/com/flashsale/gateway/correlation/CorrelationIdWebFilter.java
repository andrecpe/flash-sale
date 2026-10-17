package br.com.flashsale.gateway.correlation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Borda: garante que toda requisição tenha um X-Correlation-ID válido.
 * <ul>
 *     <li>ausente ou inválido → gera um UUID (o valor inválido é descartado, nunca logado);</li>
 *     <li>repassa o ID ao serviço de destino (header da requisição);</li>
 *     <li>devolve o ID ao cliente (header da resposta, valor único).</li>
 * </ul>
 * No WebFlux o MDC não acompanha a requisição entre threads; por isso o ID é colocado no MDC
 * apenas no momento do log de acesso.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recebido = exchange.getRequest().getHeaders().getFirst(CorrelationId.HTTP_HEADER);
        String correlationId = CorrelationId.isValid(recebido) ? recebido : CorrelationId.generate();
        long inicio = System.nanoTime();

        ServerWebExchange comId = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CorrelationId.HTTP_HEADER, correlationId)))
                .build();

        ServerHttpResponse response = comId.getResponse();
        response.beforeCommit(() -> {
            // set (e não add): substitui o header eventualmente devolvido pelo serviço de destino
            response.getHeaders().set(CorrelationId.HTTP_HEADER, correlationId);
            return Mono.empty();
        });

        return chain.filter(comId).doFinally(sinal -> registrarAcesso(comId, correlationId, inicio));
    }

    private void registrarAcesso(ServerWebExchange exchange, String correlationId, long inicio) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return; // health check do Docker a cada 15 s
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationId.MDC_KEY, correlationId)) {
            log.info("HTTP {} {} -> {} ({} ms)", exchange.getRequest().getMethod(), path,
                    exchange.getResponse().getStatusCode(), (System.nanoTime() - inicio) / 1_000_000);
        }
    }
}

