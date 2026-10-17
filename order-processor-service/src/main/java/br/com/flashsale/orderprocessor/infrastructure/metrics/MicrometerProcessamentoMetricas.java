package br.com.flashsale.orderprocessor.infrastructure.metrics;

import br.com.flashsale.orderprocessor.application.port.ProcessamentoMetricasPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas de negócio do Order Processor no Micrometer (o agent OpenTelemetry as exporta por OTLP → Prometheus).
 * Nomes no Prometheus: {@code pedidos_confirmados_total}, {@code pedidos_cancelados_total}.
 * Contadores sem tags e registrados na subida (série existe desde o zero).
 */
@Component
public class MicrometerProcessamentoMetricas implements ProcessamentoMetricasPort {

    private final Counter confirmados;
    private final Counter cancelados;

    public MicrometerProcessamentoMetricas(MeterRegistry registry) {
        this.confirmados = Counter.builder("pedidos.confirmados")
                .description("Pedidos processados com sucesso (CONFIRMED)")
                .register(registry);
        this.cancelados = Counter.builder("pedidos.cancelados")
                .description("Pedidos cancelados por falha no processamento (CANCELLED)")
                .register(registry);
    }

    @Override
    public void pedidoConfirmado() {
        confirmados.increment();
    }

    @Override
    public void pedidoCancelado() {
        cancelados.increment();
    }
}

