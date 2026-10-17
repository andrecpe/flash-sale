package br.com.flashsale.orderprocessor.infrastructure.persistence;

import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.domain.Pedido;
import br.com.flashsale.orderprocessor.domain.StatusPedido;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC (SQL explícito, sem ORM) para a tabela pedidos.
 * {@code INSERT ... ON CONFLICT DO NOTHING} garante idempotência mesmo com consumidores concorrentes
 * (ex.: {@code --scale order-processor-service=3} recebendo uma mensagem reentregue).
 * Testado contra PostgreSQL real na Etapa 9 (Testcontainers).
 */
@Repository
public class JdbcPedidoRepository implements PedidoRepositoryPort {

    private static final RowMapper<Pedido> MAPPER = (rs, i) -> new Pedido(
            rs.getObject("pedido_id", UUID.class),
            rs.getString("produto_id"),
            rs.getInt("quantidade"),
            rs.getString("cliente_cpf"),
            rs.getString("cliente_nome"),
            StatusPedido.valueOf(rs.getString("status")),
            rs.getString("motivo"),
            rs.getObject("data_hora_criacao", LocalDateTime.class),
            rs.getObject("data_hora_atualizacao", LocalDateTime.class),
            rs.getString("correlation_id"));

    private final JdbcClient jdbc;

    public JdbcPedidoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(UUID pedidoId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM pedidos WHERE pedido_id = :id)")
                .param("id", pedidoId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean salvar(Pedido pedido) {
        int inseridos = jdbc.sql("""
                        INSERT INTO pedidos (pedido_id, produto_id, quantidade, cliente_cpf, cliente_nome, status,
                                             motivo, data_hora_criacao, data_hora_atualizacao, correlation_id)
                        VALUES (:id, :produto, :quantidade, :cpf, :nome, :status,
                                :motivo, :criadoEm, :atualizadoEm, :cid)
                        ON CONFLICT (pedido_id) DO NOTHING
                        """)
                .param("id", pedido.id())
                .param("produto", pedido.produtoId())
                .param("quantidade", pedido.quantidade())
                .param("cpf", pedido.clienteCpf())
                .param("nome", pedido.clienteNome())
                .param("status", pedido.status().name())
                .param("motivo", pedido.motivo())
                .param("criadoEm", pedido.criadoEm())
                .param("atualizadoEm", pedido.atualizadoEm())
                .param("cid", pedido.correlationId())
                .update();
        return inseridos == 1;
    }

    @Override
    public Optional<Pedido> buscar(UUID pedidoId) {
        return jdbc.sql("SELECT * FROM pedidos WHERE pedido_id = :id")
                .param("id", pedidoId)
                .query(MAPPER)
                .optional();
    }
}

