package br.com.flashsale.orderprocessor.infrastructure.config;

import br.com.flashsale.orderprocessor.application.ConsultarPedidoUseCase;
import br.com.flashsale.orderprocessor.application.ProcessarPedidoUseCase;
import br.com.flashsale.orderprocessor.application.port.CompensacaoPublisherPort;
import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.application.port.ProcessamentoMetricasPort;
import br.com.flashsale.orderprocessor.domain.FalhaSimulador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Composição: liga os casos de uso (camada de aplicação, sem Spring) aos adaptadores de infraestrutura.
 */
@Configuration
public class OrderProcessorConfig {

    @Bean
    Clock clock(@Value("${app.zona:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    /** FAILURE_RATE (0.0–1.0): taxa de falha aleatória da demo; fora do intervalo a aplicação não sobe. */
    @Bean
    FalhaSimulador falhaSimulador(@Value("${app.falha.taxa:0.0}") double taxa) {
        return new FalhaSimulador(taxa, new SecureRandom());
    }

    @Bean
    ProcessarPedidoUseCase processarPedidoUseCase(PedidoRepositoryPort repositorio,
                                                  CompensacaoPublisherPort publisher,
                                                  FalhaSimulador simulador, ProcessamentoMetricasPort metricas,
                                                  Clock clock) {
        return new ProcessarPedidoUseCase(repositorio, publisher, simulador, metricas, clock);
    }

    @Bean
    ConsultarPedidoUseCase consultarPedidoUseCase(PedidoRepositoryPort repositorio) {
        return new ConsultarPedidoUseCase(repositorio);
    }

    /** Mensagens publicadas em JSON (usa o ObjectMapper do Spring Boot: JavaTimeModule, datas ISO). */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Fábrica dos listeners: mantém as configurações do application.yml (ack manual, prefetch, concorrência),
     * mas entrega a mensagem crua. O listener lê o JSON pelo contrato deste serviço; o header {@code __TypeId__}
     * (nome de classe Java do produtor) é ignorado — cada bounded context é dono do seu modelo.
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

