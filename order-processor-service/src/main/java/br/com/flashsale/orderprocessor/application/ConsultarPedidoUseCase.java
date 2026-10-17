package br.com.flashsale.orderprocessor.application;

import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.domain.Pedido;

import java.util.Optional;
import java.util.UUID;

/** Consulta de pedido pela API interna (usada pelo Checkout no cache-miss). */
public class ConsultarPedidoUseCase {

    private final PedidoRepositoryPort repositorio;

    public ConsultarPedidoUseCase(PedidoRepositoryPort repositorio) {
        this.repositorio = repositorio;
    }

    public Optional<Pedido> executar(UUID pedidoId) {
        return repositorio.buscar(pedidoId);
    }
}

