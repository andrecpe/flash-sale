package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.domain.StatusPedido;

import java.time.LocalDateTime;
import java.util.UUID;

/** Pedido consultado (visão de leitura vinda do Order Processor ou do cache). */
public record PedidoDetalhe(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        StatusPedido status,
        LocalDateTime dataHoraAtualizacao) {

    /** Sem CPF/nome: dados pessoais nunca vão para logs. */
    @Override
    public String toString() {
        return "PedidoDetalhe[pedidoId=" + pedidoId + ", produtoId=" + produtoId + ", quantidade=" + quantidade
                + ", status=" + status + ", dataHoraAtualizacao=" + dataHoraAtualizacao + "]";
    }
}

