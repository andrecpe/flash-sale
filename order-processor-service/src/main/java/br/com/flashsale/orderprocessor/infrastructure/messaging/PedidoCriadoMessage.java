package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.ProcessarPedidoCommand;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/** Contrato da mensagem consumida da fila order-created (formato do enunciado). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PedidoCriadoMessage(
        UUID pedidoId,
        String produtoId,
        Integer quantidade,
        String clienteCpf,
        String clienteNome,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraCriacao) {

    public ProcessarPedidoCommand paraComando(String correlationId) {
        return new ProcessarPedidoCommand(pedidoId, produtoId, quantidade == null ? 0 : quantidade,
                clienteCpf, clienteNome, dataHoraCriacao, correlationId);
    }

    @Override
    public String toString() {
        return "PedidoCriadoMessage[pedidoId=" + pedidoId + ", produtoId=" + produtoId + ", quantidade=" + quantidade + "]";
    }
}

