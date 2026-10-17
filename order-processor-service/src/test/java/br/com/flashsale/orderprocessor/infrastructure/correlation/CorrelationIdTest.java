package br.com.flashsale.orderprocessor.infrastructure.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @AfterEach
    void limparMdc() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7f9c24e1-3b8a-4c1d-9f2e-a1b2c3d4e5f6", "pedido_TESTE-123", "abcdefgh"})
    @DisplayName("Aceita IDs com 8 a 64 caracteres [A-Za-z0-9_-]")
    void aceitaIdsValidos(String valor) {
        assertThat(CorrelationId.isValid(valor)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "abc1234", "id com espaco", "id!invalido", "abc12345\r\nINJETADO", "<script>alert(1)</script>"})
    @DisplayName("Rejeita IDs ausentes, curtos, com caracteres proibidos ou quebra de linha (log injection)")
    void rejeitaIdsInvalidos(String valor) {
        assertThat(CorrelationId.isValid(valor)).isFalse();
    }

    @Test
    @DisplayName("Rejeita IDs com mais de 64 caracteres")
    void rejeitaIdsLongos() {
        assertThat(CorrelationId.isValid("a".repeat(65))).isFalse();
        assertThat(CorrelationId.isValid("a".repeat(64))).isTrue();
    }

    @Test
    @DisplayName("Gera IDs válidos e únicos")
    void geraIdsValidosEUnicos() {
        String primeiro = CorrelationId.generate();
        String segundo = CorrelationId.generate();

        assertThat(CorrelationId.isValid(primeiro)).isTrue();
        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("current() lê o ID do MDC")
    void currentLeDoMdc() {
        assertThat(CorrelationId.current()).isEmpty();

        MDC.put(CorrelationId.MDC_KEY, "pedido-teste-123");

        assertThat(CorrelationId.current()).contains("pedido-teste-123");
    }
}

