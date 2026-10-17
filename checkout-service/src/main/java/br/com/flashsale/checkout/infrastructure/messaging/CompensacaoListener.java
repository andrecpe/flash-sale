package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.application.CompensarEstoqueUseCase;
import br.com.flashsale.checkout.application.ResultadoCompensacao;
import br.com.flashsale.checkout.infrastructure.correlation.CorrelationIdScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de order-compensation com ack manual.
 * <ul>
 *   <li>mensagem crua: o JSON é lido pelo contrato do Checkout, ignorando o header {@code __TypeId__}
 *       (nome de classe Java do produtor — lição da Etapa 4);</li>
 *   <li>correlationId da mensagem restaurado no MDC durante todo o processamento;</li>
 *   <li>compensado ou repetido (idempotente) → {@code ack};</li>
 *   <li>qualquer erro (JSON inválido, mensagem incompleta, Redis/banco fora) → {@code nack} sem requeue:
 *       a fila encaminha para order-compensation.dlq — sem loop de reentrega.</li>
 * </ul>
 */
@Component
public class CompensacaoListener {

    private static final Logger log = LoggerFactory.getLogger(CompensacaoListener.class);

    private final CompensarEstoqueUseCase useCase;
    private final ObjectMapper objectMapper;

    public CompensacaoListener(CompensarEstoqueUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.rabbit.fila-compensacao:order-compensation}")
    public void receber(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try (CorrelationIdScope escopo = CorrelationIdScope.abrir(message)) {
            try {
                CompensacaoMessage compensacao = objectMapper.readValue(message.getBody(), CompensacaoMessage.class);
                ResultadoCompensacao resultado = useCase.executar(compensacao.paraComando(escopo.correlationId()));
                log.info("Compensação do pedido {}: {}", compensacao.pedidoId(), resultado);
                channel.basicAck(tag, false);
            } catch (Exception e) {
                log.error("Falha ao processar mensagem de order-compensation; enviada para a DLQ: {}",
                        e.getMessage(), e);
                channel.basicNack(tag, false, false);
            }
        }
    }
}

