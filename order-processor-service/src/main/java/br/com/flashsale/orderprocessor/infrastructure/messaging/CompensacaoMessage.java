package br.com.flashsale.orderprocessor.infrastructure.messaging;

import br.com.flashsale.orderprocessor.application.Compensacao;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/** Contrato da mensagem publicada na fila order-compensation (formato do enunciado). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompensacaoMessage(UUID pedidoId, String produtoId, int quantidade, String motivo) {

    public static CompensacaoMessage de(Compensacao compensacao) {
        return new CompensacaoMessage(compensacao.pedidoId(), compensacao.produtoId(),
                compensacao.quantidade(), compensacao.motivo());
    }
}

