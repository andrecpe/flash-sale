package br.com.flashsale.orderprocessor.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Etapa 4 (TDD): regra de falha simulada do processamento (aleatória para demo, determinística para testes). */
class FalhaSimuladorTest {

    /** Gerador fixo: sempre devolve o mesmo valor em nextDouble(). */
    private static RandomGenerator fixo(double valor) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return valor;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cliente FALHA", "falha total", "Maria Falha Silva"})
    @DisplayName("cliente_nome contendo FALHA (sem diferenciar maiúsculas) falha sempre, mesmo com taxa 0")
    void nomeComFalha(String nome) {
        FalhaSimulador simulador = new FalhaSimulador(0.0, new Random());

        assertThat(simulador.deveFalhar(nome)).isTrue();
    }

    @Test
    @DisplayName("FAILURE_RATE=0 nunca falha")
    void taxaZero() {
        FalhaSimulador simulador = new FalhaSimulador(0.0, fixo(0.0));

        assertThat(simulador.deveFalhar("Fernando Augusto")).isFalse();
    }

    @Test
    @DisplayName("FAILURE_RATE=1 sempre falha")
    void taxaUm() {
        FalhaSimulador simulador = new FalhaSimulador(1.0, fixo(0.999999));

        assertThat(simulador.deveFalhar("Fernando Augusto")).isTrue();
    }

    @Test
    @DisplayName("Taxa intermediária: falha quando o sorteio fica abaixo da taxa")
    void taxaIntermediaria() {
        assertThat(new FalhaSimulador(0.3, fixo(0.29)).deveFalhar("Fernando")).isTrue();
        assertThat(new FalhaSimulador(0.3, fixo(0.30)).deveFalhar("Fernando")).isFalse();
    }

    @Test
    @DisplayName("Nome ausente não quebra a regra (usa apenas a taxa)")
    void nomeNulo() {
        assertThat(new FalhaSimulador(0.0, fixo(0.0)).deveFalhar(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    @DisplayName("Taxa fora de 0.0–1.0 é rejeitada na configuração")
    void taxaInvalida(double taxa) {
        assertThatThrownBy(() -> new FalhaSimulador(taxa, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

