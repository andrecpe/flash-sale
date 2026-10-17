package br.com.flashsale.checkout.application.port;

import java.util.Optional;
import java.util.UUID;

/** Porta de saída: controle de estoque (implementada sobre Redis). */
public interface EstoquePort {

    /**
     * Reserva atômica: decrementa somente se houver saldo suficiente (nunca fica negativo).
     *
     * @return {@code true} se reservou; {@code false} se o saldo é insuficiente ou o produto não existe
     */
    boolean reservar(String produtoId, int quantidade);

    /** Devolve uma quantidade previamente reservada. */
    void liberar(String produtoId, int quantidade);

    /**
     * Compensação idempotente: marca o pedido como compensado e devolve a quantidade numa única
     * operação atômica — uma segunda chamada para o mesmo pedido não devolve nada.
     *
     * @return {@code true} se devolveu agora; {@code false} se o pedido já havia sido compensado
     */
    boolean devolverCompensacao(UUID pedidoId, String produtoId, int quantidade);

    /** Saldo disponível; vazio se o produto não tem estoque cadastrado. */
    Optional<Long> consultar(String produtoId);
}

