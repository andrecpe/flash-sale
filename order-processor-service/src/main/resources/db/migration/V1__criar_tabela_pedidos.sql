-- Etapa 4: pedidos processados pelo Order Processor (banco order_db, exclusivo deste serviço)
CREATE TABLE pedidos (
    pedido_id             UUID         PRIMARY KEY,
    produto_id            VARCHAR(64)  NOT NULL,
    quantidade            INTEGER      NOT NULL CHECK (quantidade > 0),
    cliente_cpf           VARCHAR(14)  NOT NULL,
    cliente_nome          VARCHAR(200),
    status                VARCHAR(20)  NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    motivo                VARCHAR(255),
    data_hora_criacao     TIMESTAMP    NOT NULL,
    data_hora_atualizacao TIMESTAMP    NOT NULL,
    correlation_id        VARCHAR(64)
);

-- Suporte/demo: localizar o pedido pelo X-Correlation-ID informado pelo cliente
CREATE INDEX idx_pedidos_correlation_id ON pedidos (correlation_id);

