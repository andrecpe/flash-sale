package br.com.flashsale.orderprocessor.infrastructure.web;

import br.com.flashsale.orderprocessor.domain.Pedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/** Pedido no formato do GET /v1/orders/{pedido_id} do enunciado (repassado pelo Checkout). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PedidoResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraAtualizacao) {

    static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(pedido.id(), pedido.produtoId(), pedido.quantidade(), pedido.clienteCpf(),
                pedido.clienteNome(), pedido.status().name(), pedido.atualizadoEm());
    }
}

