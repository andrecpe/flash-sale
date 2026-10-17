package br.com.flashsale.checkout.infrastructure.correlation;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

/**
 * Publicação AMQP: grava o correlationId da transação atual no header x-correlation-id.
 * O traceparent (OpenTelemetry) é injetado automaticamente pelo agent; este header carrega
 * o ID de negócio, que sobrevive inclusive a reprocessamentos/DLQ.
 */
public class CorrelationIdMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        MessageProperties props = message.getMessageProperties();
        Object atual = props.getHeader(CorrelationId.MESSAGE_HEADER);
        if (atual == null || !CorrelationId.isValid(atual.toString())) {
            props.setHeader(CorrelationId.MESSAGE_HEADER, CorrelationId.current().orElseGet(CorrelationId::generate));
        }
        return message;
    }
}

