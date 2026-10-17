package br.com.flashsale.checkout.application.port;

import br.com.flashsale.checkout.application.Compensacao;

/** Porta de saída: registro das compensações concluídas (tabela compensacoes do checkout_db). */
public interface CompensacaoRepositoryPort {

    /**
     * Registra a compensação de forma idempotente (um registro por pedido).
     *
     * @return {@code true} se inseriu; {@code false} se o pedido já tinha registro
     */
    boolean registrar(Compensacao compensacao);
}

