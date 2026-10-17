package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.CompensacaoRepositoryPort;
import br.com.flashsale.checkout.application.port.EstoquePort;
import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Passo de compensação da SAGA: o Order Processor cancelou o pedido e o Checkout devolve a reserva.
 * <ul>
 *   <li>Idempotência: o estoque é devolvido <b>uma única vez</b> por pedido (marcação + INCRBY atômicos no Redis);</li>
 *   <li>o registro em {@code compensacoes} é idempotente e sempre executado — uma reentrega (redrive da DLQ)
 *       completa uma execução anterior interrompida sem devolver o estoque de novo;</li>
 *   <li>o cache {@code pedido:{id}} é invalidado (o status mudou para CANCELLED).</li>
 * </ul>
 */
public class CompensarEstoqueUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompensarEstoqueUseCase.class);

    private final EstoquePort estoque;
    private final CompensacaoRepositoryPort repositorio;
    private final PedidoCachePort cache;
    private final PedidoMetricasPort metricas;
    private final Clock clock;

    public CompensarEstoqueUseCase(EstoquePort estoque, CompensacaoRepositoryPort repositorio,
                                   PedidoCachePort cache, PedidoMetricasPort metricas, Clock clock) {
        this.estoque = estoque;
        this.repositorio = repositorio;
        this.cache = cache;
        this.metricas = metricas;
        this.clock = clock;
    }

    /** Span de negócio "compensar-estoque" (continua o trace iniciado no gateway, via order-compensation). */
    @WithSpan("compensar-estoque")
    public ResultadoCompensacao executar(CompensarEstoqueCommand comando) {
        validar(comando);
        int quantidade = comando.quantidade();
        Telemetria.atributo("pedido.id", comando.pedidoId().toString());
        Telemetria.atributo("produto.id", comando.produtoId());
        Telemetria.atributo("pedido.quantidade", quantidade);

        boolean devolvido = estoque.devolverCompensacao(comando.pedidoId(), comando.produtoId(), quantidade);
        if (devolvido) {
            log.info("Estoque devolvido: pedido={} produto={} quantidade={}",
                    comando.pedidoId(), comando.produtoId(), quantidade);
        } else {
            log.info("Compensação repetida ignorada (estoque já devolvido): pedido={}", comando.pedidoId());
        }

        repositorio.registrar(new Compensacao(comando.pedidoId(), comando.produtoId(), quantidade, comando.motivo(),
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), comando.correlationId()));
        cache.invalidar(comando.pedidoId());

        if (devolvido) {
            metricas.compensacaoConcluida();
        }
        return devolvido ? ResultadoCompensacao.COMPENSADO : ResultadoCompensacao.IGNORADO;
    }

    private static void validar(CompensarEstoqueCommand comando) {
        if (comando.pedidoId() == null) {
            throw new MensagemInvalidaException("pedido_id é obrigatório");
        }
        if (comando.produtoId() == null || comando.produtoId().isBlank()) {
            throw new MensagemInvalidaException("produto_id é obrigatório");
        }
        if (comando.quantidade() == null || comando.quantidade() <= 0) {
            throw new MensagemInvalidaException("quantidade deve ser maior que zero");
        }
    }
}

