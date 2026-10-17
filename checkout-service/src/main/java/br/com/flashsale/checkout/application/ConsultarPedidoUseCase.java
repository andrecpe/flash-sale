package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoConsultaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * GET /v1/orders/{pedido_id} com Cache-Aside:
 * <ol>
 *   <li>busca no Redis ({@code pedido:{id}}) — no pico, a maioria das leituras para aqui;</li>
 *   <li>cache-miss: consulta o Order Processor até {@code tentativas} vezes com backoff exponencial
 *       (o pedido pode ainda estar na fila — consistência eventual);</li>
 *   <li>encontrado: grava no cache com TTL; não encontrado após as tentativas: 404.</li>
 * </ol>
 * Order Processor indisponível (circuito aberto/timeout) → falha imediata, sem retentar: insistir numa
 * dependência sobrecarregada no pico só piora o quadro (o circuit breaker já protege o serviço).
 * O Redis é otimização: falhas no cache são registradas e a consulta segue pela fonte.
 */
public class ConsultarPedidoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsultarPedidoUseCase.class);

    private final PedidoCachePort cache;
    private final PedidoConsultaPort consulta;
    private final PoliticaConsulta politica;
    private final Pausa pausa;

    public ConsultarPedidoUseCase(PedidoCachePort cache, PedidoConsultaPort consulta,
                                  PoliticaConsulta politica, Pausa pausa) {
        this.cache = cache;
        this.consulta = consulta;
        this.politica = politica;
        this.pausa = pausa;
    }

    public PedidoDetalhe executar(UUID pedidoId) {
        Optional<PedidoDetalhe> emCache = lerCache(pedidoId);
        if (emCache.isPresent()) {
            log.info("Pedido {} servido pelo cache", pedidoId);
            return emCache.get();
        }

        for (int tentativa = 1; tentativa <= politica.tentativas(); tentativa++) {
            if (tentativa > 1) {
                pausa.aguardar(politica.esperaAntesDa(tentativa));
            }
            Optional<PedidoDetalhe> encontrado = consulta.buscar(pedidoId);
            if (encontrado.isPresent()) {
                log.info("Pedido {} obtido do Order Processor na tentativa {}", pedidoId, tentativa);
                gravarCache(encontrado.get());
                return encontrado.get();
            }
            log.info("Pedido {} não encontrado (tentativa {}/{})", pedidoId, tentativa, politica.tentativas());
        }
        throw new PedidoNaoEncontradoException();
    }

    private Optional<PedidoDetalhe> lerCache(UUID pedidoId) {
        try {
            return cache.buscar(pedidoId);
        } catch (RuntimeException e) {
            log.warn("Cache indisponível na leitura do pedido {}; consultando o Order Processor: {}",
                    pedidoId, e.getMessage());
            return Optional.empty();
        }
    }

    private void gravarCache(PedidoDetalhe pedido) {
        try {
            cache.gravar(pedido, politica.ttlCache());
        } catch (RuntimeException e) {
            log.warn("Cache indisponível ao gravar o pedido {}: {}", pedido.pedidoId(), e.getMessage());
        }
    }
}

