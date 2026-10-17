package br.com.flashsale.checkout.application;

/** Não há saldo suficiente para atender o pedido (resulta em 409 Conflict). */
public class EstoqueInsuficienteException extends RuntimeException {

    private final String produtoId;

    public EstoqueInsuficienteException(String produtoId) {
        super("Estoque insuficiente para o produto " + produtoId);
        this.produtoId = produtoId;
    }

    public String produtoId() {
        return produtoId;
    }
}

