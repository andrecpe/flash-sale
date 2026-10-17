package br.com.flashsale.checkout.infrastructure.persistence;

import br.com.flashsale.checkout.application.Compensacao;
import br.com.flashsale.checkout.application.port.CompensacaoRepositoryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JDBC (SQL explícito, sem ORM) para a tabela compensacoes.
 * {@code INSERT ... ON CONFLICT DO NOTHING}: um registro por pedido, mesmo com reentregas.
 * Testado contra PostgreSQL real na Etapa 9 (Testcontainers).
 */
@Repository
public class JdbcCompensacaoRepository implements CompensacaoRepositoryPort {

    private final JdbcClient jdbc;

    public JdbcCompensacaoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrar(Compensacao compensacao) {
        int inseridos = jdbc.sql("""
                        INSERT INTO compensacoes (pedido_id, produto_id, quantidade, motivo,
                                                  data_hora_compensacao, correlation_id)
                        VALUES (:id, :produto, :quantidade, :motivo, :compensadoEm, :cid)
                        ON CONFLICT (pedido_id) DO NOTHING
                        """)
                .param("id", compensacao.pedidoId())
                .param("produto", compensacao.produtoId())
                .param("quantidade", compensacao.quantidade())
                .param("motivo", compensacao.motivo())
                .param("compensadoEm", compensacao.dataHoraCompensacao())
                .param("cid", compensacao.correlationId())
                .update();
        return inseridos == 1;
    }
}

