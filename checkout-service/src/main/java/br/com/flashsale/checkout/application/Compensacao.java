package br.com.flashsale.checkout.application;

import java.time.LocalDateTime;
import java.util.UUID;

/** Compensação concluída: estoque devolvido para o pedido cancelado pelo Order Processor. */
public record Compensacao(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String motivo,
        LocalDateTime dataHoraCompensacao,
        String correlationId) {
}

