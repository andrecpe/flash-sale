-- Etapa 5: compensações concluídas pelo Checkout (banco checkout_db, exclusivo deste serviço)
CREATE TABLE compensacoes (
    pedido_id             UUID         PRIMARY KEY,
    produto_id            VARCHAR(64)  NOT NULL,
    quantidade            INTEGER      NOT NULL CHECK (quantidade > 0),
    motivo                VARCHAR(255),
    data_hora_compensacao TIMESTAMP    NOT NULL,
    correlation_id        VARCHAR(64)
);

-- Suporte/demo: localizar a compensação pelo X-Correlation-ID informado pelo cliente
CREATE INDEX idx_compensacoes_correlation_id ON compensacoes (correlation_id);

