-- Billing Service (Fase 4, Passo 3): tabelas que eram do monolito e passaram a ser deste servico
-- (ADR 0003). Mesmo formato do oficina-app ate a V24, menos as chaves estrangeiras para clientes,
-- funcionarios e ordens de servico - esses dados moram no OS Service; aqui fica so o id e o snapshot
-- recebido no GerarOrcamento (ADR 0002).

CREATE TABLE orcamentos (
    id                         UUID           PRIMARY KEY,
    numero_orcamento           VARCHAR(255)   NOT NULL UNIQUE,
    ordem_de_servico_id        UUID           NOT NULL,   -- sem FK: OS Service
    saga_id                    UUID,                      -- sem FK: Saga mora no OS Service
    cliente_id                 UUID           NOT NULL,   -- sem FK: snapshot do cliente abaixo
    cliente_nome               VARCHAR(255)   NOT NULL,
    cliente_cpf                VARCHAR(255)   NOT NULL,
    funcionario_id             UUID           NOT NULL,   -- sem FK
    placa_veiculo              VARCHAR(255)   NOT NULL,
    marca_veiculo              VARCHAR(255)   NOT NULL,
    modelo_veiculo             VARCHAR(255)   NOT NULL,
    descricao_diagnostico      VARCHAR(4000)  NOT NULL,
    valor_mao_de_obra          NUMERIC(19, 2) NOT NULL,
    valor_pecas                NUMERIC(19, 2) NOT NULL,
    desconto                   NUMERIC(19, 2) NOT NULL,
    valor_total                NUMERIC(19, 2) NOT NULL,
    criado_em                  TIMESTAMP(6)   NOT NULL,
    validade                   TIMESTAMP(6)   NOT NULL,
    observacoes                VARCHAR(4000),
    status                     VARCHAR(255)   NOT NULL,   -- AGUARDANDO_APROVACAO, APROVADO, REJEITADO, CANCELADO
    enviado_para_aprovacao_em  TIMESTAMP(6)
);

-- Um orcamento por OS: GerarOrcamento reenviado encontra o existente (idempotencia pelo estado).
CREATE UNIQUE INDEX uk_orcamentos_ordem_de_servico ON orcamentos (ordem_de_servico_id);

CREATE TABLE orcamento_servicos_propostos (
    orcamento_id  UUID          NOT NULL REFERENCES orcamentos (id) ON DELETE CASCADE,
    servico       VARCHAR(255)  NOT NULL
);

CREATE INDEX idx_orcamento_servicos_orcamento ON orcamento_servicos_propostos (orcamento_id);

-- Snapshot de descricao e preco consultados na Execution (POST /internal/pecas-insumos/consulta).
CREATE TABLE orcamento_pecas_previstas (
    orcamento_id    UUID            NOT NULL REFERENCES orcamentos (id) ON DELETE CASCADE,
    peca_insumo_id  VARCHAR(255)    NOT NULL,             -- sem FK: Execution Service
    descricao       VARCHAR(255)    NOT NULL,
    preco           NUMERIC(19, 2)  NOT NULL,
    quantidade      INTEGER         NOT NULL CHECK (quantidade > 0)
);

CREATE INDEX idx_orcamento_pecas_orcamento ON orcamento_pecas_previstas (orcamento_id);

-- Tokens do webhook de decisao de orcamento (POST /integracoes/orcamentos/{n}/decisao).
CREATE TABLE tokens_integracao (
    id            UUID          PRIMARY KEY,
    rotulo        VARCHAR(255)  NOT NULL,
    hash_token    VARCHAR(128)  NOT NULL UNIQUE,
    status        VARCHAR(20)   NOT NULL,                 -- ATIVO, REVOGADO
    criado_em     TIMESTAMP     NOT NULL,
    criado_por    VARCHAR(255)  NOT NULL,
    revogado_em   TIMESTAMP,
    revogado_por  VARCHAR(255)
);

-- Outbox transacional dos eventos para o orquestrador (ADR 0005).
CREATE TABLE outbox_mensagens (
    id            UUID          PRIMARY KEY,               -- = messageId
    fila_destino  VARCHAR(120)  NOT NULL,
    tipo          VARCHAR(60)   NOT NULL,
    group_id      VARCHAR(60)   NOT NULL,                  -- = ordemServicoId
    payload       TEXT          NOT NULL,                  -- envelope completo
    criada_em     TIMESTAMP     NOT NULL,
    publicada_em  TIMESTAMP,
    tentativas    INTEGER       NOT NULL DEFAULT 0,
    ultimo_erro   TEXT
);

CREATE INDEX idx_outbox_mensagens_pendentes ON outbox_mensagens (criada_em) WHERE publicada_em IS NULL;

-- Idempotencia do consumidor de billing-commands: messageId gravado na transacao do efeito.
CREATE TABLE mensagens_processadas (
    mensagem_id    UUID         PRIMARY KEY,
    tipo           VARCHAR(60)  NOT NULL,
    processada_em  TIMESTAMP    NOT NULL
);
