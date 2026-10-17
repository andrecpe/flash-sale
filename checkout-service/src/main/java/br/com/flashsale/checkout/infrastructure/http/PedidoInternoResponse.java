package br.com.flashsale.checkout.infrastructure.http;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.domain.StatusPedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contrato (do Checkout) da resposta de GET /internal/v1/orders/{id} do Order Processor — formato do enunciado.
 * Duplicado de propósito: cada bounded context é dono do seu modelo.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
record PedidoInternoResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        StatusPedido status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraAtualizacao) {

    PedidoDetalhe paraDetalhe() {
        return new PedidoDetalhe(pedidoId, produtoId, quantidade, clienteCpf, clienteNome, status,
                dataHoraAtualizacao);
    }
}

