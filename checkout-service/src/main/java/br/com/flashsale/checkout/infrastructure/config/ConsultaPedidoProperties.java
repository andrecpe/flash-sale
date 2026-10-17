package br.com.flashsale.checkout.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Consulta de pedidos (Etapa 6): API interna do Order Processor, retentativas, cache e circuit breaker.
 * Valores em {@code app.consulta-pedido.*} (application.yml).
 */
@ConfigurationProperties(prefix = "app.consulta-pedido")
public record ConsultaPedidoProperties(
        String orderProcessorUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int tentativas,
        Duration esperaInicial,
        Duration ttlCache,
        CircuitBreakerProps circuitBreaker) {

    public record CircuitBreakerProps(
            int janela,
            int chamadasMinimas,
            float taxaFalhaPercentual,
            Duration esperaAberto) {
    }
}

