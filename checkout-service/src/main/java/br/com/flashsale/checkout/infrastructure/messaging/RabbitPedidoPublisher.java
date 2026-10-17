package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.application.PublicacaoIndisponivelException;
import br.com.flashsale.checkout.application.port.PedidoPublisherPort;
import br.com.flashsale.checkout.domain.Pedido;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationIdMessagePostProcessor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Publica em order-created (exchange orders.exchange, routing key order-created).
 * <p>
 * Publisher confirms: só retorna depois que o broker confirmar que recebeu a mensagem (persistente).
 * Sem confirmação dentro do timeout → {@link PublicacaoIndisponivelException} e o caso de uso desfaz a reserva.
 * O header x-correlation-id é gravado pelo {@link CorrelationIdMessagePostProcessor};
 * o traceparent (OpenTelemetry) é injetado automaticamente pelo agent.
 */
@Component
public class RabbitPedidoPublisher implements PedidoPublisherPort {

    static final String EXCHANGE = "orders.exchange";
    static final String ROUTING_KEY = "order-created";

    private final RabbitTemplate rabbit;
    private final CorrelationIdMessagePostProcessor correlationId = new CorrelationIdMessagePostProcessor();
    private final long timeoutConfirmacaoMs;

    public RabbitPedidoPublisher(RabbitTemplate rabbit,
                                 @Value("${app.rabbit.confirm-timeout:5s}") Duration timeoutConfirmacao) {
        this.rabbit = rabbit;
        this.timeoutConfirmacaoMs = timeoutConfirmacao.toMillis();
    }

    @Override
    public void publicarPedidoCriado(Pedido pedido) {
        try {
            rabbit.invoke(operacoes -> {
                operacoes.convertAndSend(EXCHANGE, ROUTING_KEY, PedidoCriadoMessage.de(pedido), correlationId);
                operacoes.waitForConfirmsOrDie(timeoutConfirmacaoMs);
                return null;
            });
        } catch (AmqpException e) {
            throw new PublicacaoIndisponivelException("Broker não confirmou a publicação do pedido " + pedido.id(), e);
        }
    }
}

