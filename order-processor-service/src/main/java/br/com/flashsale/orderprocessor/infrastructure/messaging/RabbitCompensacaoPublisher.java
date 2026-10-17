package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.Compensacao;
import br.com.flashsale.orderprocessor.application.port.CompensacaoPublisherPort;
import br.com.flashsale.orderprocessor.infrastructure.correlation.CorrelationIdMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Publica em order-compensation (exchange orders.exchange, routing key order-compensation).
 * <p>
 * Publisher confirms: só retorna após o broker confirmar (mensagem persistente). Sem confirmação,
 * a {@link org.springframework.amqp.AmqpException} propaga: a transação do pedido é desfeita e a mensagem
 * original vai para a DLQ. O header x-correlation-id é gravado pelo {@link CorrelationIdMessagePostProcessor}.
 */
@Component
public class RabbitCompensacaoPublisher implements CompensacaoPublisherPort {

    static final String EXCHANGE = "orders.exchange";
    static final String ROUTING_KEY = "order-compensation";

    private final RabbitTemplate rabbit;
    private final CorrelationIdMessagePostProcessor correlationId = new CorrelationIdMessagePostProcessor();
    private final long timeoutConfirmacaoMs;

    public RabbitCompensacaoPublisher(RabbitTemplate rabbit,
                                      @Value("${app.rabbit.confirm-timeout:5s}") Duration timeoutConfirmacao) {
        this.rabbit = rabbit;
        this.timeoutConfirmacaoMs = timeoutConfirmacao.toMillis();
    }

    @Override
    public void publicar(Compensacao compensacao) {
        rabbit.invoke(operacoes -> {
            operacoes.convertAndSend(EXCHANGE, ROUTING_KEY, CompensacaoMessage.de(compensacao), correlationId);
            operacoes.waitForConfirmsOrDie(timeoutConfirmacaoMs);
            return null;
        });
    }
}

