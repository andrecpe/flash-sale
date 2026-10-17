package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.ProcessarPedidoUseCase;
import br.com.flashsale.orderprocessor.application.ResultadoProcessamento;
import br.com.flashsale.orderprocessor.infrastructure.correlation.CorrelationIdScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;

/**
 * Consumidor de order-created com ack manual.
 * <ul>
 *   <li>correlationId da mensagem restaurado no MDC durante todo o processamento;</li>
 *   <li>processamento (gravação + publicação da compensação) dentro de uma transação de banco;</li>
 *   <li>sucesso ou pedido duplicado → {@code ack};</li>
 *   <li>qualquer erro (JSON inválido, mensagem incompleta, banco/broker fora) → {@code nack} sem requeue:
 *       a fila order-created encaminha para a DLQ (order-created.dlq) — sem loop de reentrega.</li>
 * </ul>
 */
@Component
public class PedidoCriadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoListener.class);

    private final ProcessarPedidoUseCase useCase;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transacao;

    public PedidoCriadoListener(ProcessarPedidoUseCase useCase, ObjectMapper objectMapper,
                                TransactionOperations transacao) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
        this.transacao = transacao;
    }

    @RabbitListener(queues = "${app.rabbit.fila-pedidos:order-created}")
    public void receber(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try (CorrelationIdScope escopo = CorrelationIdScope.abrir(message)) {
            try {
                PedidoCriadoMessage pedido = objectMapper.readValue(message.getBody(), PedidoCriadoMessage.class);
                ResultadoProcessamento resultado =
                        transacao.execute(status -> useCase.executar(pedido.paraComando(escopo.correlationId())));
                log.info("Pedido {} processado: {}", pedido.pedidoId(), resultado);
                channel.basicAck(tag, false);
            } catch (Exception e) {
                log.error("Falha ao processar mensagem de order-created; enviada para a DLQ: {}", e.getMessage(), e);
                channel.basicNack(tag, false, false);
            }
        }
    }
}

