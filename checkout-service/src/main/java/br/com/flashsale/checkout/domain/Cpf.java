package br.com.flashsale.checkout.domain;

import java.util.regex.Pattern;

/**
 * Value object CPF: somente dígitos, com dígitos verificadores válidos.
 * Aceita a entrada com ou sem máscara (503.292.910-76 ou 50329291076).
 */
public record Cpf(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}$");

    public Cpf {
        if (!isValido(valor) || valor.length() != 11) {
            throw new IllegalArgumentException("CPF inválido");
        }
    }

    public static Cpf de(String bruto) {
        if (!isValido(bruto)) {
            throw new IllegalArgumentException("CPF inválido");
        }
        return new Cpf(bruto.replaceAll("\\D", ""));
    }

    public static boolean isValido(String bruto) {
        if (bruto == null || !FORMATO.matcher(bruto).matches()) {
            return false;
        }
        String digitos = bruto.replaceAll("\\D", "");
        if (digitos.chars().distinct().count() == 1) {
            return false; // 000.000.000-00, 111.111.111-11, ...
        }
        return digitoVerificador(digitos, 9) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Mascarado: o CPF completo nunca deve aparecer em logs (LGPD). */
    @Override
    public String toString() {
        return CpfMasker.mascarar(valor);
    }
}

