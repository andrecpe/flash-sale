package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.EstoquePort;
import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
import br.com.flashsale.checkout.application.port.PedidoPublisherPort;
import br.com.flashsale.checkout.domain.Pedido;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Passo 1 da SAGA: valida o pedido, reserva o estoque de forma atômica e publica em order-created.
 * <p>
 * Consistência local: se a publicação falhar, a reserva é desfeita imediatamente — nenhuma unidade
 * fica "presa" no Redis sem um pedido correspondente na fila.
 */
public class CriarPedidoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CriarPedidoUseCase.class);

    private final EstoquePort estoque;
    private final PedidoPublisherPort publisher;
    private final PedidoMetricasPort metricas;
    private final Clock clock;

    public CriarPedidoUseCase(EstoquePort estoque, PedidoPublisherPort publisher, PedidoMetricasPort metricas,
                              Clock clock) {
        this.estoque = estoque;
        this.publisher = publisher;
        this.metricas = metricas;
        this.clock = clock;
    }

    public Pedido executar(CriarPedidoCommand comando) {
        Pedido pedido = Pedido.criar(comando.produtoId(), comando.quantidade(), comando.clienteCpf(),
                comando.clienteNome(), clock);

        if (!reservarEstoque(pedido)) {
            log.info("Estoque insuficiente: produto={} quantidade={}", pedido.produtoId(), pedido.quantidade());
            metricas.pedidoRejeitadoPorEstoque();
            throw new EstoqueInsuficienteException(pedido.produtoId());
        }
        log.info("Estoque reservado: pedido={} produto={} quantidade={}",
                pedido.id(), pedido.produtoId(), pedido.quantidade());

        try {
            publisher.publicarPedidoCriado(pedido);
        } catch (RuntimeException falha) {
            estoque.liberar(pedido.produtoId(), pedido.quantidade());
            log.warn("Falha ao publicar pedido={}; reserva desfeita", pedido.id(), falha);
            throw falha instanceof PublicacaoIndisponivelException indisponivel
                    ? indisponivel
                    : new PublicacaoIndisponivelException("Falha ao publicar o pedido", falha);
        }
        metricas.pedidoCriado();
        log.info("Pedido publicado em order-created: pedido={}", pedido.id());
        return pedido;
    }

    /** Span de negócio "reservar-estoque" (filho do span HTTP; o Redis aparece abaixo dele). Sem CPF/nome. */
    @WithSpan("reservar-estoque")
    boolean reservarEstoque(Pedido pedido) {
        Telemetria.atributo("pedido.id", pedido.id().toString());
        Telemetria.atributo("produto.id", pedido.produtoId());
        Telemetria.atributo("pedido.quantidade", pedido.quantidade());
        return estoque.reservar(pedido.produtoId(), pedido.quantidade());
    }
}

