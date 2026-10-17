package br.com.flashsale.checkout.application;

import java.util.UUID;

/** Dados recebidos da fila order-compensation (ainda não validados). */
public record CompensarEstoqueCommand(
        UUID pedidoId,
        String produtoId,
        Integer quantidade,
        String motivo,
        String correlationId) {
}

