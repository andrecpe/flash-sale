package br.com.flashsale.checkout.infrastructure.metrics;

import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas de negócio do Checkout no Micrometer (o agent OpenTelemetry as exporta por OTLP → Prometheus).
 * Nomes no Prometheus: {@code pedidos_criados_total}, {@code pedidos_rejeitados_estoque_total},
 * {@code compensacoes_total}. Contadores sem tags e registrados na subida (série existe desde o zero).
 */
@Component
public class MicrometerPedidoMetricas implements PedidoMetricasPort {

    private final Counter criados;
    private final Counter rejeitadosEstoque;
    private final Counter compensacoes;

    public MicrometerPedidoMetricas(MeterRegistry registry) {
        this.criados = Counter.builder("pedidos.criados")
                .description("Pedidos aceitos (estoque reservado e publicados em order-created)")
                .register(registry);
        this.rejeitadosEstoque = Counter.builder("pedidos.rejeitados.estoque")
                .description("Pedidos rejeitados por estoque insuficiente (409)")
                .register(registry);
        this.compensacoes = Counter.builder("compensacoes")
                .description("Compensações concluídas (estoque devolvido para pedido cancelado)")
                .register(registry);
    }

    @Override
    public void pedidoCriado() {
        criados.increment();
    }

    @Override
    public void pedidoRejeitadoPorEstoque() {
        rejeitadosEstoque.increment();
    }

    @Override
    public void compensacaoConcluida() {
        compensacoes.increment();
    }
}

