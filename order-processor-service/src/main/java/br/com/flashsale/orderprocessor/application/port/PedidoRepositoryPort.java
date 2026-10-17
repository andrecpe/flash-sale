package br.com.flashsale.orderprocessor.application.port;

import br.com.flashsale.orderprocessor.domain.Pedido;

import java.util.Optional;
import java.util.UUID;

/** Porta de persistência dos pedidos (banco próprio do Order Processor). */
public interface PedidoRepositoryPort {

    boolean existe(UUID pedidoId);

    /**
     * Grava o pedido.
     *
     * @return {@code false} se outro consumidor já gravou o mesmo pedido (idempotência sob concorrência)
     */
    boolean salvar(Pedido pedido);

    Optional<Pedido> buscar(UUID pedidoId);
}

