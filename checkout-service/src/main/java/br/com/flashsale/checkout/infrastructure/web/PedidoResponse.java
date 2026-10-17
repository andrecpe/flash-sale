package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resposta 200 do GET /v1/orders/{pedido_id} (formato do enunciado). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PedidoResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraAtualizacao) {

    static PedidoResponse de(PedidoDetalhe p) {
        return new PedidoResponse(p.pedidoId(), p.produtoId(), p.quantidade(), p.clienteCpf(), p.clienteNome(),
                p.status().name(), p.dataHoraAtualizacao());
    }
}

