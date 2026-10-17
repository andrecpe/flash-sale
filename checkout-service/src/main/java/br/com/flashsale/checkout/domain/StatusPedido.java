package br.com.flashsale.checkout.domain;

/** Ciclo de vida do pedido na SAGA. O Checkout cria em PROCESSING; o Order Processor decide o desfecho. */
public enum StatusPedido {
    PROCESSING,
    CONFIRMED,
    CANCELLED
}

