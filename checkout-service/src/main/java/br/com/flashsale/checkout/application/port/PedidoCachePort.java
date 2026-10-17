package br.com.flashsale.checkout.application.port;

import br.com.flashsale.checkout.application.PedidoDetalhe;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída: cache de consulta de pedidos ({@code pedido:{pedido_id}}). */
public interface PedidoCachePort {

    /** Pedido em cache; vazio no cache-miss. */
    Optional<PedidoDetalhe> buscar(UUID pedidoId);

    /** Grava o pedido com validade {@code ttl}. */
    void gravar(PedidoDetalhe pedido, Duration ttl);

    /** Remove o pedido do cache (ex.: após compensação, o status passou a CANCELLED). */
    void invalidar(UUID pedidoId);
}

