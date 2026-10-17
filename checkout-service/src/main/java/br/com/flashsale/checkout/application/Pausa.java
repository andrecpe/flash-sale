package br.com.flashsale.checkout.application;

import java.time.Duration;

/** Pausa entre tentativas (injetável: nos testes não há espera real). */
@FunctionalInterface
public interface Pausa {

    void aguardar(Duration duracao);
}

