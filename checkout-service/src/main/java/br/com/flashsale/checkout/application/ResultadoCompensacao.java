package br.com.flashsale.checkout.application;

/** Resultado da compensação. */
public enum ResultadoCompensacao {
    /** Estoque devolvido nesta execução. */
    COMPENSADO,
    /** Pedido já compensado anteriormente: nada devolvido (idempotência). */
    IGNORADO
}

