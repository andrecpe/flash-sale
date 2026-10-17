package br.com.flashsale.checkout.domain;

/**
 * Mascaramento de CPF para qualquer saída observável (log, span, métrica): {@code 503.***.***-76}.
 * Entrada que não parece um CPF (11 dígitos) é totalmente mascarada — nunca devolve o original.
 */
public final class CpfMasker {

    private static final String TOTAL = "***";

    private CpfMasker() {
    }

    public static String mascarar(String cpf) {
        if (cpf == null) {
            return TOTAL;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11 || !cpf.matches("[\\d.\\-]+")) {
            return TOTAL;
        }
        return digitos.substring(0, 3) + ".***.***-" + digitos.substring(9);
    }
}

