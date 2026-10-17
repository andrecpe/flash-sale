package br.com.flashsale.checkout.infrastructure.messaging;

import br.com.flashsale.checkout.application.CompensarEstoqueCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Contrato (do Checkout) da mensagem consumida da fila order-compensation — formato do enunciado.
 * Duplicado de propósito em relação ao Order Processor: cada bounded context é dono do seu modelo.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompensacaoMessage(UUID pedidoId, String produtoId, Integer quantidade, String motivo) {

    public CompensarEstoqueCommand paraComando(String correlationId) {
        return new CompensarEstoqueCommand(pedidoId, produtoId, quantidade, motivo, correlationId);
    }
}

