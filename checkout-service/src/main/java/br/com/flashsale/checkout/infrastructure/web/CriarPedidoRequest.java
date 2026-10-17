package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.CriarPedidoCommand;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Corpo do POST /v1/orders. A validação das regras fica no domínio (fonte única da verdade). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CriarPedidoRequest(String produtoId, Integer quantidade, String clienteCpf, String clienteNome) {

    CriarPedidoCommand paraComando() {
        return new CriarPedidoCommand(produtoId, quantidade, clienteCpf, clienteNome);
    }
}

