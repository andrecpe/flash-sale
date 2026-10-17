package br.com.flashsale.checkout.infrastructure.config;

import br.com.flashsale.checkout.application.ConsultarPedidoUseCase;
import br.com.flashsale.checkout.application.PoliticaConsulta;
import br.com.flashsale.checkout.application.ServicoIndisponivelException;
import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoConsultaPort;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationIdClientHttpRequestInterceptor;
import br.com.flashsale.checkout.infrastructure.http.OrderProcessorClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Etapa 6: composição da consulta de pedidos (cache-aside + API interna do Order Processor). */
@Configuration
@EnableConfigurationProperties(ConsultaPedidoProperties.class)
public class ConsultaPedidoConfig {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPedidoConfig.class);

    @Bean
    CircuitBreaker orderProcessorCircuitBreaker(ConsultaPedidoProperties props) {
        ConsultaPedidoProperties.CircuitBreakerProps cb = props.circuitBreaker();
        CircuitBreaker circuito = CircuitBreaker.of("order-processor", CircuitBreakerConfig.custom()
                .slidingWindowSize(cb.janela())
                .minimumNumberOfCalls(cb.chamadasMinimas())
                .failureRateThreshold(cb.taxaFalhaPercentual())
                .waitDurationInOpenState(cb.esperaAberto())
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        circuito.getEventPublisher().onStateTransition(evento ->
                log.warn("Circuit breaker order-processor: {}", evento.getStateTransition()));
        return circuito;
    }

    @Bean
    PedidoConsultaPort orderProcessorClient(RestClient.Builder builder, ConsultaPedidoProperties props,
                                            CircuitBreaker orderProcessorCircuitBreaker) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(props.connectTimeout());
        fabrica.setReadTimeout(props.readTimeout());
        RestClient restClient = builder.clone()
                .baseUrl(props.orderProcessorUrl())
                .requestFactory(fabrica)
                .requestInterceptor(new CorrelationIdClientHttpRequestInterceptor())
                .build();
        return new OrderProcessorClient(restClient, orderProcessorCircuitBreaker);
    }

    @Bean
    ConsultarPedidoUseCase consultarPedidoUseCase(PedidoCachePort cache, PedidoConsultaPort consulta,
                                                  ConsultaPedidoProperties props) {
        PoliticaConsulta politica = new PoliticaConsulta(props.tentativas(), props.esperaInicial(), props.ttlCache());
        return new ConsultarPedidoUseCase(cache, consulta, politica, ConsultaPedidoConfig::dormir);
    }

    /** Pausa real entre tentativas (virtual threads: não prende thread de plataforma). */
    private static void dormir(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicoIndisponivelException("Consulta interrompida", e);
        }
    }
}




