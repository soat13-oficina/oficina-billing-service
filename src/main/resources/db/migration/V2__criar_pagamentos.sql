-- Cobranca de um orcamento aprovado (ADR 0007), extraida do oficina-app (la, V24). Referencias do
-- Mercado Pago nas colunas mp_*; idempotency_key e a X-Idempotency-Key de todas as chamadas da cobranca.
CREATE TABLE pagamentos (
    id                   UUID           PRIMARY KEY,
    orcamento_id         UUID           NOT NULL REFERENCES orcamentos (id),
    ordem_de_servico_id  UUID           NOT NULL,             -- sem FK: OS Service
    saga_id              UUID           NOT NULL,
    valor                NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(30)    NOT NULL,             -- PENDENTE, APROVADO, RECUSADO, EXPIRADO, ESTORNADO, CANCELADO
    motivo               VARCHAR(500),
    mp_preference_id     VARCHAR(80),
    mp_payment_id        VARCHAR(40)    UNIQUE,
    mp_refund_id         VARCHAR(40),
    link_pagamento       VARCHAR(1000),
    expira_em            TIMESTAMP      NOT NULL,
    idempotency_key      UUID           NOT NULL UNIQUE,
    versao               BIGINT         NOT NULL DEFAULT 0,
    criado_em            TIMESTAMP      NOT NULL,
    atualizado_em        TIMESTAMP      NOT NULL
);

CREATE INDEX idx_pagamentos_orcamento ON pagamentos (orcamento_id);

-- O expirador so le cobrancas pendentes.
CREATE INDEX idx_pagamentos_pendentes ON pagamentos (expira_em) WHERE status = 'PENDENTE';
