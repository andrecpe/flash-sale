package br.com.flashsale.orderprocessor.application;

import java.time.LocalDateTime;
import java.util.UUID;

/** Dados do pedido recebido em order-created + correlationId da transação. */
public record ProcessarPedidoCommand(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        LocalDateTime dataHoraCriacao,
        String correlationId) {

    @Override
    public String toString() {
        return "ProcessarPedidoCommand[pedidoId=" + pedidoId + ", produtoId=" + produtoId
                + ", quantidade=" + quantidade + "]";
    }
}

