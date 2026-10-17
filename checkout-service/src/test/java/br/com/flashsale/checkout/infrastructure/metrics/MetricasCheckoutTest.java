package br.com.flashsale.checkout.infrastructure.metrics;

import br.com.flashsale.checkout.application.port.EstoquePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Etapa 8 (TDD): métricas de negócio do Checkout. Baixa cardinalidade: contadores sem tags;
 * o gauge de estoque tem apenas a tag produto_id (conjunto fixo, vindo da configuração da campanha).
 */
class MetricasCheckoutTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerPedidoMetricas metricas = new MicrometerPedidoMetricas(registry);

    private double contador(String nome) {
        return registry.get(nome).counter().count();
    }

    @Test
    @DisplayName("pedidos.criados: incrementa a cada pedido aceito")
    void pedidosCriados() {
        metricas.pedidoCriado();
        metricas.pedidoCriado();

        assertThat(contador("pedidos.criados")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("pedidos.rejeitados.estoque: incrementa a cada 409")
    void pedidosRejeitados() {
        metricas.pedidoRejeitadoPorEstoque();

        assertThat(contador("pedidos.rejeitados.estoque")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("compensacoes: incrementa a cada estoque devolvido")
    void compensacoes() {
        metricas.compensacaoConcluida();

        assertThat(contador("compensacoes")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Contadores nascem zerados e sem tags (disponíveis desde o início)")
    void contadoresSemTags() {
        assertThat(contador("pedidos.criados")).isZero();
        assertThat(registry.get("pedidos.criados").counter().getId().getTags()).isEmpty();
        assertThat(registry.get("compensacoes").counter().getId().getTags()).isEmpty();
    }

    @Nested
    class GaugeEstoque {

        private final EstoquePort estoque = mock(EstoquePort.class);

        @BeforeEach
        void setUp() {
            new EstoqueDisponivelGauges(registry, estoque, List.of("PROD-001", "PROD-002")).registrar();
        }

        @Test
        @DisplayName("estoque.disponivel{produto_id}: lê o saldo atual no momento da coleta")
        void leSaldoAtual() {
            when(estoque.consultar("PROD-001")).thenReturn(Optional.of(98L));
            when(estoque.consultar("PROD-002")).thenReturn(Optional.of(10L));

            assertThat(registry.get("estoque.disponivel").tag("produto_id", "PROD-001").gauge().value()).isEqualTo(98.0);
            assertThat(registry.get("estoque.disponivel").tag("produto_id", "PROD-002").gauge().value()).isEqualTo(10.0);

            when(estoque.consultar("PROD-001")).thenReturn(Optional.of(97L));
            assertThat(registry.get("estoque.disponivel").tag("produto_id", "PROD-001").gauge().value()).isEqualTo(97.0);
        }

        @Test
        @DisplayName("Produto sem estoque cadastrado ou Redis fora: NaN (a coleta de métricas nunca falha)")
        void semSaldoOuRedisFora() {
            when(estoque.consultar("PROD-001")).thenReturn(Optional.empty());
            when(estoque.consultar("PROD-002")).thenThrow(new IllegalStateException("redis fora"));

            assertThat(registry.get("estoque.disponivel").tag("produto_id", "PROD-001").gauge().value()).isNaN();
            assertThat(registry.get("estoque.disponivel").tag("produto_id", "PROD-002").gauge().value()).isNaN();
        }
    }
}


