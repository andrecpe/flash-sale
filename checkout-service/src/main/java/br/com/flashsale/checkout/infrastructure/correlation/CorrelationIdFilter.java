package br.com.flashsale.checkout.infrastructure.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Entrada HTTP: lê o X-Correlation-ID (enviado pelo gateway), valida, guarda no MDC durante a
 * requisição, devolve na resposta e limpa o MDC ao final (threads do pool são reutilizadas).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String recebido = request.getHeader(CorrelationId.HTTP_HEADER);
        String correlationId = CorrelationId.isValid(recebido) ? recebido : CorrelationId.generate();
        long inicio = System.nanoTime();

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HTTP_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                log.info("HTTP {} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
            }
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}

