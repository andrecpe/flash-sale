package br.com.flashsale.orderprocessor.infrastructure.correlation;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

/**
 * Consumo AMQP: restaura o correlationId da mensagem no MDC durante o processamento.
 * Uso nos listeners:
 * <pre>
 * try (var escopo = CorrelationIdScope.abrir(message)) {
 *     // processa; todo log sai com o correlationId da transação
 * }
 * </pre>
 * Ao fechar, devolve o MDC ao estado anterior (a thread do listener é reutilizada).
 */
public final class CorrelationIdScope implements AutoCloseable {

    private final String correlationId;
    private final String anterior;

    private CorrelationIdScope(String correlationId) {
        this.correlationId = correlationId;
        this.anterior = MDC.get(CorrelationId.MDC_KEY);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
    }

    public static CorrelationIdScope abrir(Message message) {
        Object header = message.getMessageProperties().getHeader(CorrelationId.MESSAGE_HEADER);
        String recebido = header == null ? null : header.toString();
        return new CorrelationIdScope(CorrelationId.isValid(recebido) ? recebido : CorrelationId.generate());
    }

    public String correlationId() {
        return correlationId;
    }

    @Override
    public void close() {
        if (anterior == null) {
            MDC.remove(CorrelationId.MDC_KEY);
        } else {
            MDC.put(CorrelationId.MDC_KEY, anterior);
        }
    }
}

