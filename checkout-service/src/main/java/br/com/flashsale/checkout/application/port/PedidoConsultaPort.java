package br.com.flashsale.checkout.application.port;

import br.com.flashsale.checkout.application.PedidoDetalhe;

import java.util.Optional;
import java.util.UUID;

/** Porta de saída: consulta do pedido na API interna do Order Processor (dono do dado). */
public interface PedidoConsultaPort {

    /**
     * @return o pedido; vazio se o Order Processor ainda não o conhece
     * @throws br.com.flashsale.checkout.application.ServicoIndisponivelException circuito aberto, timeout,
     *                                                                            erro de rede ou 5xx
     */
    Optional<PedidoDetalhe> buscar(UUID pedidoId);
}

