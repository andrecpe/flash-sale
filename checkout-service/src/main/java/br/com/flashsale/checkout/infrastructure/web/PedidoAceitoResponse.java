package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.domain.Pedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resposta 202 Accepted do POST /v1/orders. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PedidoAceitoResponse(
        UUID pedidoId,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraRequisicao) {

    static PedidoAceitoResponse de(Pedido pedido) {
        return new PedidoAceitoResponse(pedido.id(), pedido.status().name(), pedido.criadoEm());
    }
}

