package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.MensagemInvalidaException;
import br.com.flashsale.orderprocessor.application.ProcessarPedidoCommand;
import br.com.flashsale.orderprocessor.application.ProcessarPedidoUseCase;
import br.com.flashsale.orderprocessor.application.ResultadoProcessamento;
import br.com.flashsale.orderprocessor.infrastructure.correlation.CorrelationId;
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
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Etapa 4 (TDD): listener de order-created com ack manual; erro → nack sem requeue (DLQ). */
@ExtendWith(MockitoExtension.class)
class PedidoCriadoListenerTest {

    private static final long TAG = 42L;
    private static final String CORPO = """
            {"pedido_id":"b819cc65-f6f0-478c-8bb7-69ee1c4f6402","produto_id":"PROD-001","quantidade":2,
             "cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto","data_hora_criacao":"2026-09-07T20:03:57"}
            """;

    @Mock
    private ProcessarPedidoUseCase useCase;
    @Mock
    private Channel channel;

    private PedidoCriadoListener listener;

    @BeforeEach
    void setUp() {
        listener = new PedidoCriadoListener(useCase, JsonMapper.builder().findAndAddModules().build(),
                TransactionOperations.withoutTransaction());
        MDC.clear();
    }

    private static Message mensagem(String corpo, String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        if (correlationId != null) {
            props.setHeader(CorrelationId.MESSAGE_HEADER, correlationId);
        }
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    @DisplayName("Mensagem válida: processa com o correlationId no MDC e confirma (ack)")
    void processaEConfirma() throws Exception {
        AtomicReference<String> cidDuranteProcessamento = new AtomicReference<>();
        when(useCase.executar(any())).thenAnswer(inv -> {
            cidDuranteProcessamento.set(MDC.get(CorrelationId.MDC_KEY));
            return ResultadoProcessamento.CONFIRMADO;
        });

        listener.receber(mensagem(CORPO, "demo-etapa4-0001"), channel);

        ArgumentCaptor<ProcessarPedidoCommand> comando = ArgumentCaptor.forClass(ProcessarPedidoCommand.class);
        verify(useCase).executar(comando.capture());
        assertThat(comando.getValue().correlationId()).isEqualTo("demo-etapa4-0001");
        assertThat(comando.getValue().produtoId()).isEqualTo("PROD-001");
        assertThat(cidDuranteProcessamento.get()).isEqualTo("demo-etapa4-0001");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("Pedido duplicado (ignorado) também é confirmado")
    void duplicadoConfirma() throws Exception {
        when(useCase.executar(any())).thenReturn(ResultadoProcessamento.IGNORADO);

        listener.receber(mensagem(CORPO, null), channel);

        verify(channel).basicAck(TAG, false);
    }

    @Test
    @DisplayName("JSON inválido: rejeita sem requeue (DLQ) e não processa")
    void jsonInvalido() throws Exception {
        listener.receber(mensagem("{ quebrado", "demo-etapa4-0002"), channel);

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
    @DisplayName("Erro de infraestrutura (banco/broker): rejeita sem requeue (DLQ) e limpa o MDC")
    void erroInfra() throws Exception {
        when(useCase.executar(any())).thenThrow(new IllegalStateException("banco fora"));

        listener.receber(mensagem(CORPO, "demo-etapa4-0003"), channel);

        verify(channel).basicNack(TAG, false, false);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}

