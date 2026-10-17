package br.com.flashsale.gateway.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contrato do Correlation ID na borda. O gateway é o ponto onde o ID nasce (ou é validado, se vier do cliente).
 */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-ID";
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
}

