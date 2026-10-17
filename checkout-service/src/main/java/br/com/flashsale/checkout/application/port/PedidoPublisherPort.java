package br.com.flashsale.checkout.application.port;

import br.com.flashsale.checkout.application.PublicacaoIndisponivelException;
import br.com.flashsale.checkout.domain.Pedido;

/** Porta de saída: publicação do evento de pedido criado (implementada sobre RabbitMQ, fila order-created). */
public interface PedidoPublisherPort {

    /**
     * Publica e só retorna após a confirmação do broker.
     *
     * @throws PublicacaoIndisponivelException se o broker não confirmar
     */
    void publicarPedidoCriado(Pedido pedido);
}

