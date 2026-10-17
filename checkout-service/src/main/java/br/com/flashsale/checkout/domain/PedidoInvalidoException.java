package br.com.flashsale.checkout.domain;

import java.util.List;

/** Violação das regras de criação do pedido. Reúne todos os erros para responder de uma vez ao cliente. */
public class PedidoInvalidoException extends RuntimeException {

    private final List<String> erros;

    public PedidoInvalidoException(List<String> erros) {
        super(String.join("; ", erros));
        this.erros = List.copyOf(erros);
    }

    public List<String> erros() {
        return erros;
    }
}

