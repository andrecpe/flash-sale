package br.com.flashsale.checkout.application.port;

/**
 * Porta de saída: métricas de negócio do Checkout.
 * Contadores sem tags (baixa cardinalidade): nunca usar pedido_id, cliente ou CPF como label.
 */
public interface PedidoMetricasPort {

    /** Pedido aceito (estoque reservado e publicado em order-created). */
    void pedidoCriado();

    /** Pedido rejeitado por estoque insuficiente (409). */
    void pedidoRejeitadoPorEstoque();

    /** Compensação concluída: estoque devolvido pela primeira vez para um pedido cancelado. */
    void compensacaoConcluida();
}

