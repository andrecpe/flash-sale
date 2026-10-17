package br.com.flashsale.checkout.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 8 (TDD): CPF nunca aparece por inteiro em log, span ou métrica (LGPD). */
class CpfMaskerTest {

    @Test
    @DisplayName("50329291076 → 503.***.***-76")
    void mascaraSoDigitos() {
        assertThat(CpfMasker.mascarar("50329291076")).isEqualTo("503.***.***-76");
    }

    @Test
    @DisplayName("Entrada já formatada (503.292.910-76) gera a mesma máscara")
    void mascaraFormatado() {
        assertThat(CpfMasker.mascarar("503.292.910-76")).isEqualTo("503.***.***-76");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "abc", "5032929107", "503292910761", "  "})
    @DisplayName("Valor que não parece um CPF: máscara total (nunca devolve o original)")
    void valorInvalido(String entrada) {
        assertThat(CpfMasker.mascarar(entrada)).isEqualTo("***");
    }

    @Test
    @DisplayName("Nulo → máscara total")
    void nulo() {
        assertThat(CpfMasker.mascarar(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("O value object Cpf usa o mesmo mascaramento no toString")
    void cpfToString() {
        assertThat(Cpf.de("50329291076").toString()).isEqualTo("503.***.***-76");
    }
}

