package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.domain.Pedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/** Contrato da mensagem publicada na fila order-created (formato do enunciado). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PedidoCriadoMessage(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraCriacao) {

    public static PedidoCriadoMessage de(Pedido pedido) {
        return new PedidoCriadoMessage(pedido.id(), pedido.produtoId(), pedido.quantidade(),
                pedido.cpf().valor(), pedido.clienteNome(), pedido.criadoEm());
    }

    @Override
    public String toString() {
        return "PedidoCriadoMessage[pedidoId=" + pedidoId + ", produtoId=" + produtoId + ", quantidade=" + quantidade + "]";
    }
}

