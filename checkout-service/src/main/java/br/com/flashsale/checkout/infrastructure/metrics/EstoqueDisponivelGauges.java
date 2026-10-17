package br.com.flashsale.checkout.infrastructure.metrics;

import br.com.flashsale.checkout.application.port.EstoquePort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

/**
 * Gauge {@code estoque.disponivel{produto_id}} (Prometheus: {@code estoque_disponivel}): saldo atual no Redis,
 * lido no momento da coleta. Cardinalidade limitada: somente os produtos da campanha (configuração).
 * Falha ao ler (Redis fora, produto sem estoque) → NaN: a coleta de métricas nunca falha.
 */
public class EstoqueDisponivelGauges {

    private final MeterRegistry registry;
    private final EstoquePort estoque;
    private final List<String> produtos;

    public EstoqueDisponivelGauges(MeterRegistry registry, EstoquePort estoque, List<String> produtos) {
        this.registry = registry;
        this.estoque = estoque;
        this.produtos = List.copyOf(produtos);
    }

    public void registrar() {
        for (String produtoId : produtos) {
            Gauge.builder("estoque.disponivel", () -> saldo(produtoId))
                    .description("Saldo de estoque disponível no Redis")
                    .tag("produto_id", produtoId)
                    .strongReference(true)
                    .register(registry);
        }
    }

    private double saldo(String produtoId) {
        try {
            return estoque.consultar(produtoId).map(Long::doubleValue).orElse(Double.NaN);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}

