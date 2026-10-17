package br.com.flashsale.orderprocessor.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 8 (TDD): métricas de negócio do Order Processor (contadores sem tags: baixa cardinalidade). */
class MetricasProcessamentoTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerProcessamentoMetricas metricas = new MicrometerProcessamentoMetricas(registry);

    @Test
    @DisplayName("pedidos.confirmados: incrementa a cada pedido CONFIRMED")
    void confirmados() {
        metricas.pedidoConfirmado();
        metricas.pedidoConfirmado();

        assertThat(registry.get("pedidos.confirmados").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("pedidos.cancelados: incrementa a cada pedido CANCELLED")
    void cancelados() {
        metricas.pedidoCancelado();

        assertThat(registry.get("pedidos.cancelados").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Contadores nascem zerados e sem tags (disponíveis desde o início)")
    void semTags() {
        assertThat(registry.get("pedidos.confirmados").counter().count()).isZero();
        assertThat(registry.get("pedidos.cancelados").counter().getId().getTags()).isEmpty();
    }
}


