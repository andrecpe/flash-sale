package br.com.flashsale.checkout.infrastructure.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interpreta a variável ESTOQUE_INICIAL no formato {@code PROD-001:100,PROD-002:10}.
 * Valor mal formado interrompe a subida (falha rápida, em vez de rodar sem estoque).
 */
public final class EstoqueInicialParser {

    private EstoqueInicialParser() {
    }

    public static Map<String, Long> parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Long> estoque = new LinkedHashMap<>();
        for (String item : valor.split(",")) {
            if (item.isBlank()) {
                continue;
            }
            String[] partes = item.split(":", -1);
            if (partes.length != 2 || partes[0].isBlank()) {
                throw invalido(item);
            }
            long quantidade;
            try {
                quantidade = Long.parseLong(partes[1].trim());
            } catch (NumberFormatException e) {
                throw invalido(item);
            }
            if (quantidade < 0) {
                throw invalido(item);
            }
            estoque.put(partes[0].trim(), quantidade);
        }
        return estoque;
    }

    private static IllegalArgumentException invalido(String item) {
        return new IllegalArgumentException(
                "ESTOQUE_INICIAL inválido em '" + item.trim() + "'. Formato esperado: PROD-001:100,PROD-002:10");
    }
}

