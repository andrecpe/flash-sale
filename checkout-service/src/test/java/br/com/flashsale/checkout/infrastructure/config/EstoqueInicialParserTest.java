package br.com.flashsale.checkout.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstoqueInicialParserTest {

    @Test
    @DisplayName("Interpreta 'PROD-001:100,PROD-002:10' preservando a ordem")
    void interpretaLista() {
        Map<String, Long> estoque = EstoqueInicialParser.parse("PROD-001:100,PROD-002:10");

        assertThat(estoque).containsExactly(Map.entry("PROD-001", 100L), Map.entry("PROD-002", 10L));
    }

    @Test
    @DisplayName("Ignora espaços e itens vazios")
    void ignoraEspacos() {
        assertThat(EstoqueInicialParser.parse(" PROD-001 : 5 , , PROD-002:0 "))
                .containsExactly(Map.entry("PROD-001", 5L), Map.entry("PROD-002", 0L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Valor ausente: nenhum estoque a carregar")
    void vazio(String valor) {
        assertThat(EstoqueInicialParser.parse(valor)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROD-001", "PROD-001:", ":10", "PROD-001:abc", "PROD-001:-1", "PROD-001:1:2"})
    @DisplayName("Item mal formado ou quantidade negativa: erro na subida (falha rápida)")
    void itemInvalido(String valor) {
        assertThatThrownBy(() -> EstoqueInicialParser.parse(valor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ESTOQUE_INICIAL");
    }
}

