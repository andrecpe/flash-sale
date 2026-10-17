package br.com.flashsale.checkout.application;

import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Etapa 8 (TDD): atributos dos spans de negócio — nunca com dados pessoais. */
class TelemetriaTest {

    private final Span span = mock(Span.class);

    @ParameterizedTest
    @ValueSource(strings = {"pedido.id", "produto.id", "pedido.quantidade"})
    @DisplayName("Atributos de negócio sem dados pessoais são gravados no span")
    void gravaAtributosPermitidos(String chave) {
        Telemetria.atributo(span, chave, "valor");

        verify(span).setAttribute(chave, "valor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente.cpf", "cliente_cpf", "CPF", "cliente.nome", "cliente_nome", "cliente.email"})
    @DisplayName("Atributos que parecem dados pessoais (cpf, nome, cliente) são descartados")
    void descartaDadosPessoais(String chave) {
        Telemetria.atributo(span, chave, "50329291076");

        verifyNoInteractions(span);
    }

    @Test
    @DisplayName("Valor numérico é gravado como número")
    void atributoNumerico() {
        Telemetria.atributo(span, "pedido.quantidade", 2L);

        verify(span).setAttribute("pedido.quantidade", 2L);
    }

    @Test
    @DisplayName("Valor nulo é ignorado (sem exceção no fluxo de negócio)")
    void valorNulo() {
        Telemetria.atributo(span, "pedido.id", (String) null);

        verifyNoInteractions(span);
    }

    @Test
    @DisplayName("Sem agent/span ativo, a chamada pública é inofensiva")
    void semSpanAtivo() {
        Telemetria.atributo("pedido.id", "b819cc65-f6f0-478c-8bb7-69ee1c4f6402");

        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }
}

