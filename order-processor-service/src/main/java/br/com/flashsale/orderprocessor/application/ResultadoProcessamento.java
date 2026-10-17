package br.com.flashsale.orderprocessor.application;

/** Resultado do processamento de uma mensagem de order-created. */
public enum ResultadoProcessamento {
    CONFIRMADO,
    CANCELADO,
    /** Pedido já processado antes (mensagem duplicada/reentregue): nada a fazer. */
    IGNORADO
}

