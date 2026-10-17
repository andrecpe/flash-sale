package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.application.CompensarEstoqueCommand;
import br.com.flashsale.checkout.application.CompensarEstoqueUseCase;
import br.com.flashsale.checkout.application.MensagemInvalidaException;
import br.com.flashsale.checkout.application.ResultadoCompensacao;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationId;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Etapa 5 (TDD): listener de order-compensation com ack manual; erro → nack sem requeue (DLQ).
 * Recebe a mensagem crua: o header {@code __TypeId__} do produtor é ignorado (lição da Etapa 4).
 */
@ExtendWith(MockitoExtension.class)
class CompensacaoListenerTest {

    private static final long TAG = 7L;
    private static final String CORPO = """
            {"pedido_id":"b819cc65-f6f0-478c-8bb7-69ee1c4f6402","produto_id":"PROD-001","quantidade":2,
             "motivo":"Falha no processamento do pedido"}
            """;

    @Mock
    private CompensarEstoqueUseCase useCase;
    @Mock
    private Channel channel;

    private CompensacaoListener listener;

    @BeforeEach
    void setUp() {
        listener = new CompensacaoListener(useCase, JsonMapper.builder().findAndAddModules().build());
        MDC.clear();
    }

    private static Message mensagem(String corpo, String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        // header gravado pelo Jackson2JsonMessageConverter do produtor: classe que não existe neste serviço
        props.setHeader("__TypeId__", "br.com.flashsale.orderprocessor.infrastructure.messaging.CompensacaoMessage");
        if (correlationId != null) {
            props.setHeader(CorrelationId.MESSAGE_HEADER, correlationId);
        }
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    @DisplayName("Mensagem válida: compensa com o correlationId no MDC e confirma (ack)")
    void compensaEConfirma() throws Exception {
        AtomicReference<String> cidDurante = new AtomicReference<>();
        when(useCase.executar(any())).thenAnswer(inv -> {
            cidDurante.set(MDC.get(CorrelationId.MDC_KEY));
            return ResultadoCompensacao.COMPENSADO;
        });

        listener.receber(mensagem(CORPO, "demo-etapa5-0001"), channel);

        ArgumentCaptor<CompensarEstoqueCommand> comando = ArgumentCaptor.forClass(CompensarEstoqueCommand.class);
        verify(useCase).executar(comando.capture());
        assertThat(comando.getValue().correlationId()).isEqualTo("demo-etapa5-0001");
        assertThat(comando.getValue().produtoId()).isEqualTo("PROD-001");
        assertThat(comando.getValue().quantidade()).isEqualTo(2);
        assertThat(cidDurante.get()).isEqualTo("demo-etapa5-0001");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("Compensação repetida (ignorada) também é confirmada")
    void repetidaConfirma() throws Exception {
        when(useCase.executar(any())).thenReturn(ResultadoCompensacao.IGNORADO);

        listener.receber(mensagem(CORPO, null), channel);

        verify(channel).basicAck(TAG, false);
    }

    @Test
    @DisplayName("JSON inválido: rejeita sem requeue (DLQ) e não compensa")
    void jsonInvalido() throws Exception {
        listener.receber(mensagem("{ quebrado", "demo-etapa5-0002"), channel);

        verifyNoInteractions(useCase);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    @DisplayName("Mensagem incompleta: rejeita sem requeue (DLQ)")
    void mensagemIncompleta() throws Exception {
        when(useCase.executar(any())).thenThrow(new MensagemInvalidaException("produto_id é obrigatório"));

        listener.receber(mensagem("{\"pedido_id\":\"b819cc65-f6f0-478c-8bb7-69ee1c4f6402\"}", null), channel);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("Erro de infraestrutura (Redis/banco): rejeita sem requeue (DLQ) e limpa o MDC")
    void erroInfra() throws Exception {
        when(useCase.executar(any())).thenThrow(new IllegalStateException("redis fora"));

        listener.receber(mensagem(CORPO, "demo-etapa5-0003"), channel);

        verify(channel).basicNack(TAG, false, false);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}

