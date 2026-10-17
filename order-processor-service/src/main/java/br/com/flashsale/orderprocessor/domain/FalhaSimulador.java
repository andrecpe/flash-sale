package br.com.flashsale.orderprocessor.domain;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Simula falha no processamento (o "pagamento" do enunciado):
 * <ul>
 *   <li>determinística: {@code cliente_nome} contendo {@code FALHA} (sem diferenciar maiúsculas) falha sempre;</li>
 *   <li>aleatória: falha com probabilidade {@code taxa} (FAILURE_RATE, 0.0–1.0).</li>
 * </ul>
 */
public class FalhaSimulador {

    private static final String GATILHO = "FALHA";

    private final double taxa;
    private final RandomGenerator random;

    public FalhaSimulador(double taxa, RandomGenerator random) {
        if (!(taxa >= 0.0 && taxa <= 1.0)) {
            throw new IllegalArgumentException("FAILURE_RATE deve estar entre 0.0 e 1.0: " + taxa);
        }
        this.taxa = taxa;
        this.random = random;
    }

    public boolean deveFalhar(String clienteNome) {
        if (clienteNome != null && clienteNome.toUpperCase(Locale.ROOT).contains(GATILHO)) {
            return true;
        }
        return taxa > 0.0 && random.nextDouble() < taxa;
    }
}

