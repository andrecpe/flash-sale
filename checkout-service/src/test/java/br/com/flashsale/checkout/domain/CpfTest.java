package br.com.flashsale.checkout.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"50329291076", "503.292.910-76", "52998224725"})
    @DisplayName("Aceita CPF com dígitos verificadores corretos (com ou sem máscara)")
    void aceitaCpfValido(String valor) {
        assertThat(Cpf.isValido(valor)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"50329291077", "11111111111", "1234567890", "503292910761", "abc.def.ghi-jk", "503-292-910.76"})
    @DisplayName("Rejeita CPF ausente, com dígito verificador errado, repetido ou mal formatado")
    void rejeitaCpfInvalido(String valor) {
        assertThat(Cpf.isValido(valor)).isFalse();
    }

    @Test
    @DisplayName("Normaliza para somente dígitos")
    void normalizaParaDigitos() {
        assertThat(Cpf.de("503.292.910-76").valor()).isEqualTo("50329291076");
    }

    @Test
    @DisplayName("Criar com valor inválido lança exceção")
    void criarInvalidoLancaExcecao() {
        assertThatThrownBy(() -> Cpf.de("50329291077")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString mascara o CPF (LGPD: nunca logar o CPF completo)")
    void toStringMascarado() {
        assertThat(Cpf.de("50329291076").toString()).isEqualTo("503.***.***-76");
    }
}

