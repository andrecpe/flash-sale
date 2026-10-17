package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.EstoquePort;

import java.util.Optional;

/** Consulta o saldo disponível de um produto (apoio à demonstração e ao suporte). */
public class ConsultarEstoqueUseCase {

    private final EstoquePort estoque;

    public ConsultarEstoqueUseCase(EstoquePort estoque) {
        this.estoque = estoque;
    }

    public Optional<Long> executar(String produtoId) {
        return estoque.consultar(produtoId);
    }
}

