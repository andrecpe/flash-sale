package br.com.flashsale.checkout.infrastructure.config;

import br.com.flashsale.checkout.application.CompensarEstoqueUseCase;
import br.com.flashsale.checkout.application.ConsultarEstoqueUseCase;
import br.com.flashsale.checkout.application.CriarPedidoUseCase;
import br.com.flashsale.checkout.application.port.CompensacaoRepositoryPort;
import br.com.flashsale.checkout.application.port.EstoquePort;
import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
import br.com.flashsale.checkout.application.port.PedidoPublisherPort;
import br.com.flashsale.checkout.infrastructure.metrics.EstoqueDisponivelGauges;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/**
 * Composição: liga os casos de uso (camada de aplicação, sem Spring) aos adaptadores de infraestrutura.
 */
@Configuration
public class CheckoutConfig {

    @Bean
    Clock clock(@Value("${app.zona:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    @Bean
    CriarPedidoUseCase criarPedidoUseCase(EstoquePort estoque, PedidoPublisherPort publisher,
                                          PedidoMetricasPort metricas, Clock clock) {
        return new CriarPedidoUseCase(estoque, publisher, metricas, clock);
    }

    @Bean
    ConsultarEstoqueUseCase consultarEstoqueUseCase(EstoquePort estoque) {
        return new ConsultarEstoqueUseCase(estoque);
    }

    @Bean
    CompensarEstoqueUseCase compensarEstoqueUseCase(EstoquePort estoque, CompensacaoRepositoryPort repositorio,
                                                    PedidoCachePort cache, PedidoMetricasPort metricas, Clock clock) {
        return new CompensarEstoqueUseCase(estoque, repositorio, cache, metricas, clock);
    }

    /** Gauge estoque.disponivel{produto_id} para os produtos da campanha (cardinalidade fixa). */
    @Bean
    EstoqueDisponivelGauges estoqueDisponivelGauges(MeterRegistry registry, EstoquePort estoque,
                                                    @Value("${estoque.inicial:}") String configuracao) {
        EstoqueDisponivelGauges gauges = new EstoqueDisponivelGauges(registry, estoque,
                List.copyOf(EstoqueInicialParser.parse(configuracao).keySet()));
        gauges.registrar();
        return gauges;
    }

    /** Mensagens em JSON (usa o ObjectMapper do Spring Boot: JavaTimeModule, datas ISO). */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Fábrica dos listeners: mantém as configurações do application.yml (ack manual, prefetch, concorrência),
     * mas entrega a mensagem crua. O listener lê o JSON pelo contrato deste serviço; o header {@code __TypeId__}
     * (nome de classe Java do produtor) é ignorado — cada bounded context é dono do seu modelo (lição da Etapa 4).
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }
}

