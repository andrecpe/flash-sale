package br.com.flashsale.checkout.application;

/** O pedido não existe no Order Processor após todas as tentativas. */
public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException() {
        super("Pedido não encontrado");
    }
}

