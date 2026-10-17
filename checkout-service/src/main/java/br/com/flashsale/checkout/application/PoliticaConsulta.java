package br.com.flashsale.checkout.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Política da consulta de pedidos.
 *
 * @param tentativas    consultas ao Order Processor antes do 404 (consistência eventual: pedido ainda na fila)
 * @param esperaInicial pausa antes da 2ª tentativa; dobra a cada nova tentativa (backoff exponencial)
 * @param ttlCache      validade do pedido no cache {@code pedido:{id}}
 */
public record PoliticaConsulta(int tentativas, Duration esperaInicial, Duration ttlCache) {

    public PoliticaConsulta {
        if (tentativas < 1) {
            throw new IllegalArgumentException("tentativas deve ser >= 1");
        }
        Objects.requireNonNull(esperaInicial, "esperaInicial");
        Objects.requireNonNull(ttlCache, "ttlCache");
        if (esperaInicial.isNegative() || ttlCache.isNegative() || ttlCache.isZero()) {
            throw new IllegalArgumentException("esperaInicial deve ser >= 0 e ttlCache > 0");
        }
    }

    /** Pausa antes da tentativa {@code n} (n >= 2): espera inicial × 2^(n-2). */
    Duration esperaAntesDa(int tentativa) {
        return esperaInicial.multipliedBy(1L << (tentativa - 2));
    }
}

