package br.com.flashsale.checkout.application;

/** Dados de entrada do caso de uso de criação de pedido (ainda não validados). */
public record CriarPedidoCommand(String produtoId, Integer quantidade, String clienteCpf, String clienteNome) {

    @Override
    public String toString() {
        // não expõe CPF/nome em logs
        return "CriarPedidoCommand[produtoId=" + produtoId + ", quantidade=" + quantidade + "]";
    }
}

