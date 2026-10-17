package br.com.flashsale.orderprocessor.application.port;

/**
 * Porta de saída: métricas de negócio do Order Processor.
 * Contadores sem tags (baixa cardinalidade): nunca usar pedido_id, cliente ou CPF como label.
 */
public interface ProcessamentoMetricasPort {

    /** Pedido gravado como CONFIRMED. */
    void pedidoConfirmado();

    /** Pedido gravado como CANCELLED e compensação publicada. */
    void pedidoCancelado();
}

