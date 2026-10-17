package br.com.flashsale.checkout.infrastructure.correlation;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contrato do Correlation ID: identifica a transação de negócio (o pedido) de ponta a ponta,
 * inclusive através do RabbitMQ. Complementa o trace_id do OpenTelemetry (que é técnico).
 */
public final class CorrelationId {

    /** Header HTTP (entrada e saída). */
    public static final String HTTP_HEADER = "X-Correlation-ID";
    /** Header das mensagens AMQP. */
    public static final String MESSAGE_HEADER = "x-correlation-id";
    /** Chave no MDC (aparece em todo log pelo pattern logging.pattern.correlation). */
    public static final String MDC_KEY = "correlationId";

    /** Header é entrada externa: tamanho e caracteres restritos evitam log injection e DoS de log. */
    private static final Pattern FORMATO_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private CorrelationId() {
    }

    public static boolean isValid(String valor) {
        return valor != null && FORMATO_VALIDO.matcher(valor).matches();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** ID da transação em andamento na thread atual (vazio fora de uma requisição/mensagem). */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}

