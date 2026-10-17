package br.com.flashsale.orderprocessor.application;

import java.util.UUID;

/** Pedido de devolução de estoque enviado ao Checkout (fila order-compensation). */
public record Compensacao(UUID pedidoId, String produtoId, int quantidade, String motivo) {
}

