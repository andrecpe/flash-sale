package br.com.flashsale.checkout.infrastructure.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdMessagesTest {

    private final CorrelationIdMessagePostProcessor postProcessor = new CorrelationIdMessagePostProcessor();

    @AfterEach
    void limparMdc() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    private static Message mensagem(String correlationId) {
        MessageProperties props = new MessageProperties();
        if (correlationId != null) {
            props.setHeader(CorrelationId.MESSAGE_HEADER, correlationId);
        }
        return new Message("{}".getBytes(StandardCharsets.UTF_8), props);
    }

    // ---------------- Publicação ----------------

    @Test
    @DisplayName("Publicação: copia o correlationId do MDC para o header x-correlation-id")
    void publicacaoCopiaDoMdc() {
        MDC.put(CorrelationId.MDC_KEY, "pedido-teste-12345678");

        Message enviada = postProcessor.postProcessMessage(mensagem(null));

        assertThat((String) enviada.getMessageProperties().getHeader(CorrelationId.MESSAGE_HEADER))
                .isEqualTo("pedido-teste-12345678");
    }

    @Test
    @DisplayName("Publicação sem contexto (ex.: job): gera um novo correlationId")
    void publicacaoSemContextoGeraId() {
        Message enviada = postProcessor.postProcessMessage(mensagem(null));

        String header = enviada.getMessageProperties().getHeader(CorrelationId.MESSAGE_HEADER);
        assertThat(CorrelationId.isValid(header)).isTrue();
    }

    @Test
    @DisplayName("Publicação: não sobrescreve um header válido já definido")
    void publicacaoNaoSobrescreveHeaderValido() {
        MDC.put(CorrelationId.MDC_KEY, "contexto-atual-123");

        Message enviada = postProcessor.postProcessMessage(mensagem("definido-antes-123"));

        assertThat((String) enviada.getMessageProperties().getHeader(CorrelationId.MESSAGE_HEADER))
                .isEqualTo("definido-antes-123");
    }

    // ---------------- Consumo ----------------

    @Test
    @DisplayName("Consumo: restaura o correlationId da mensagem no MDC e limpa ao fechar o escopo")
    void consumoRestauraELimpa() {
        try (CorrelationIdScope escopo = CorrelationIdScope.abrir(mensagem("pedido-teste-12345678"))) {
            assertThat(escopo.correlationId()).isEqualTo("pedido-teste-12345678");
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("pedido-teste-12345678");
        }
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Consumo: mensagem sem header ou com header inválido recebe um novo ID")
    void consumoSemHeaderGeraId() {
        try (CorrelationIdScope escopo = CorrelationIdScope.abrir(mensagem("abc\r\nINJETADO"))) {
            assertThat(CorrelationId.isValid(escopo.correlationId())).isTrue();
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo(escopo.correlationId());
        }
        try (CorrelationIdScope escopo = CorrelationIdScope.abrir(mensagem(null))) {
            assertThat(CorrelationId.isValid(escopo.correlationId())).isTrue();
        }
    }

    @Test
    @DisplayName("Consumo: ao fechar, restaura o correlationId anterior da thread")
    void consumoRestauraValorAnterior() {
        MDC.put(CorrelationId.MDC_KEY, "valor-anterior-123");

        try (CorrelationIdScope ignored = CorrelationIdScope.abrir(mensagem("pedido-teste-12345678"))) {
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("pedido-teste-12345678");
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("valor-anterior-123");
    }
}

