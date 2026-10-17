package br.com.flashsale.gateway.correlation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"7f9c24e1-3b8a-4c1d-9f2e-a1b2c3d4e5f6", "pedido_TESTE-123", "abcdefgh"})
    @DisplayName("Aceita IDs com 8 a 64 caracteres [A-Za-z0-9_-]")
    void aceitaIdsValidos(String valor) {
        assertThat(CorrelationId.isValid(valor)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc1234", "id com espaco", "abc12345\r\nINJETADO"})
    @DisplayName("Rejeita IDs ausentes, curtos, com caracteres proibidos ou quebra de linha")
    void rejeitaIdsInvalidos(String valor) {
        assertThat(CorrelationId.isValid(valor)).isFalse();
    }

    @Test
    @DisplayName("Gera IDs válidos e únicos")
    void geraIdsValidosEUnicos() {
        assertThat(CorrelationId.isValid(CorrelationId.generate())).isTrue();
        assertThat(CorrelationId.generate()).isNotEqualTo(CorrelationId.generate());
    }
}

