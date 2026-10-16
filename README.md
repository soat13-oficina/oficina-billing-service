# oficina-billing-service

Billing Service da oficina mecânica — **orçamentos e pagamentos (Mercado Pago)**.
Tech Challenge SOAT 13, Fase 4: um dos três microsserviços que substituem o monólito
`oficina-app`.

> **Status (Passo 3, em andamento):** orçamento extraído do `oficina-app` — participante da Saga
> por SQS, banco próprio, tokens de integração e webhook de decisão. Próxima entrega: módulo de
> pagamento com o Mercado Pago (Checkout Pro). Ainda sem deploy (AWS desligada).

## Responsabilidade

| Faz | Não faz |
|---|---|
| Gera o orçamento a partir do diagnóstico (comando `GerarOrcamento` da Saga), com preços consultados no cadastro de peças | Não abre nem altera ordens de serviço — dono: OS Service |
| Alteração, aprovação e rejeição do orçamento (cliente por JWT, funcionário, webhook de decisão com `X-Webhook-Token`) | Não reserva peças — dono: Execution Service |
| Cobrança no Mercado Pago (Checkout Pro), webhook, estorno *(próxima entrega)* | Não acessa o banco de nenhum outro serviço |
| Publica os eventos que movem a Saga (`OrcamentoGerado`, `OrcamentoAprovado`…) | Não consulta clientes: o snapshot vem no comando |

Banco: PostgreSQL próprio (`oficina-billing-db-<env>`), esquema pelo Flyway
(`src/main/resources/db/migration`), validado pelo Hibernate na subida (`ddl-auto: validate`).

## Arquitetura e decisões

As decisões da Fase 4 vivem no repositório que coordena a Saga (`oficina-app`):

- [ADR 0003 — divisão em microsserviços e dono dos dados](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0003-divisao-em-microsservicos-e-dono-dos-dados.md)
- [ADR 0004 — Saga orquestrada](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0004-saga-orquestrada.md)
- [ADR 0005 — SQS FIFO, outbox e idempotência](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0005-sqs-fifo-outbox-e-idempotencia.md)
- [ADR 0007 — Mercado Pago Checkout Pro](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0007-mercado-pago-checkout-pro.md)
- [ADR 0008 — exposição por ALB](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0008-exposicao-e-roteamento.md)
- [Contratos das mensagens](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/contratos/mensagens.md) e [REST interno](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/contratos/rest-interno.md)
- [Convenções de nomes](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/arquitetura/convencoes-de-nomes.md)

### Pacotes

| Pacote | Conteúdo |
|---|---|
| `orcamento` | Domínio, casos de uso, API REST, webhook de decisão e o participante da Saga (`application/saga`). Veio do monólito sem dependência de `cliente`, `ordemservico` ou `pecainsumo` |
| `orcamento/infrastructure/catalogo` | `CatalogoPecasRest`: preço e descrição das peças por `POST /internal/pecas-insumos/consulta` (OS Service no Passo 3, Execution no Passo 4) |
| `integracao` | Tokens do webhook de decisão (`/integracoes/tokens`) e o `WebhookTokenFilter` |
| `common/infrastructure/mensageria` | Outbox, publicador, consumidor idempotente e transporte SQS (receptor com long polling) |
| `common/infrastructure/seguranca` | Validação do JWT e regras de acesso |

## Saga: o que o Billing recebe e publica

Comandos lidos de `oficina-<env>-billing-commands.fifo` (`ReceptorSqs` → `ConsumidorMensagens`,
idempotente por `messageId` em `mensagens_processadas`); eventos gravados no outbox e publicados em
`oficina-<env>-os-saga-replies.fifo`, com `causationId` = o comando.

| Comando | Resposta |
|---|---|
| `GerarOrcamento` | `OrcamentoGerado` ou `OrcamentoFalhou` (com `pecasInvalidas` quando há peça fora do cadastro). Comando repetido encontra o orçamento da OS e só repete a resposta |
| `ReverterAprovacao` | `AprovacaoRevertida` (faltou estoque: o orçamento volta a aguardar o cliente) |
| `CancelarOrcamento` | `OrcamentoCancelado` (prazo de decisão vencido ou pagamento recusado/expirado) |

Eventos espontâneos: `OrcamentoAprovado` / `OrcamentoRejeitado` (decisão do cliente, do funcionário
ou do webhook, com o canal), gravados na mesma transação da decisão.

Mensagem fora do contrato vai direto para a DLQ; falha de processamento segura as mensagens seguintes
da mesma OS até o visibility timeout (5 tentativas → DLQ). Sem SQS habilitado nada sai do outbox.

## Segurança

Valida o JWT com o mesmo segredo do OS Service e da Lambda (`JWT_SECRET`,
`oficina/<env>/jwt-secret`). Não emite token e não tem tabela de usuários:

| Token | Origem | Papel |
|---|---|---|
| `tipo = CLIENTE` | Lambda `token` (login por CPF) | `ROLE_CLIENTE`; `clienteId` e `nome` vêm das claims |
| `tipo = FUNCIONARIO` ou sem `tipo` | OS Service (`POST /api/auth/login`) | `ROLE_FUNCIONARIO` (+ `roles`, se houver) |
| outro `tipo`, expirado, assinatura inválida | — | anônimo → `401` |

Regras de acesso:

- **Cliente só enxerga e decide o próprio orçamento** (claim `clienteId`): o de outro cliente responde
  `404`, como se não existisse. Token de cliente sem `clienteId` não enxerga nenhum.
- Alterar (`PUT`) e excluir (`DELETE`) orçamento e gerir tokens de integração: só funcionário (`403`).
- `POST /integracoes/orcamentos/{n}/decisao`: sem JWT, autenticado pelo `X-Webhook-Token` — um token
  gerado em `/integracoes/tokens` ou o segredo estático `ORCAMENTO_WEBHOOK_SECRET` (vazio = só tokens
  gerados).
- Públicos: `/actuator/health/**`, `/actuator/prometheus` (em nuvem só na porta 8081, fora do
  Ingress) e o Swagger.

Chamadas que este serviço **faz** para `/internal/**` levam `X-Service-Token` (`SERVICE_TOKEN`,
`oficina/<env>/service-token`).

## Configuração

| Variável | Padrão (local) | Uso |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | `jdbc:postgresql://localhost:5434/oficina_billing` | Banco próprio |
| `JWT_SECRET` | segredo de desenvolvimento | Mesmo do OS e da Lambda |
| `SERVICE_TOKEN` | vazio | `X-Service-Token` das chamadas internas |
| `CATALOGO_PECAS_URL` | `http://localhost:8080` | Quem responde `/internal/pecas-insumos/consulta` |
| `ORCAMENTO_WEBHOOK_SECRET` | vazio | Segredo estático do webhook de decisão |
| `MENSAGERIA_SQS_HABILITADO` | `false` | Liga o SQS (envio e receptor) |
| `AMBIENTE` | `local` | Nome das filas: `oficina-<ambiente>-<fila>.fifo` |
| `AWS_ENDPOINT_URL_SQS`, `AWS_REGION` | vazio, `us-east-1` | LocalStack no desenvolvimento |

## Rodando localmente

```sh
docker-compose up -d postgres              # Postgres do Billing na porta 5434
SERVER_PORT=8081 ./mvnw spring-boot:run    # a 8080 fica para o OS Service
```

- API: `http://localhost:8081` · Swagger: `/swagger-ui.html` · Health: `/actuator/health`

Para a Saga de ponta a ponta, o LocalStack com as filas sobe pelo `docker-compose.yml` do
`oficina-app` (`docs/arquitetura/mensageria-local.md` lá): rode o Billing com
`MENSAGERIA_SQS_HABILITADO=true AWS_ENDPOINT_URL_SQS=http://localhost:4566 AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test SERVICE_TOKEN=token-de-servico-local CATALOGO_PECAS_URL=http://localhost:8080`.

## Testes e cobertura

```sh
./mvnw clean verify      # Docker precisa estar rodando (Testcontainers)
```

- Banco real: os testes com contexto Spring sobem um PostgreSQL pelo Testcontainers (`jdbc:tc:`),
  com as migrations do Flyway e `ddl-auto=validate`.
- SQS: `TransporteSqsLocalStackTest` contra o LocalStack 4.14.0 (as imagens 2026.x exigem token).
- Contrato: `ContratoDasMensagensTest` processa o exemplo de cada comando documentado e valida todo
  evento publicado contra os JSON Schemas copiados em `src/test/resources/contratos/`.
- **Gate do JaCoCo: mínimo de 80% de linhas e de branches no serviço inteiro** — o build falha
  abaixo disso. Relatório em `target/site/jacoco/index.html` e como artefato `jacoco-report` no CI.

## CI/CD

| Job | Quando | O que faz |
|---|---|---|
| `testes` | PR e push | Build, testes e gate de cobertura |
| `qualidade` | PR e push | SonarCloud com quality gate bloqueante (exige o secret `SONAR_TOKEN`) |
| `validacao` | PR e push | `kubectl kustomize` dos overlays `hml` e `prd` |
| `guard` | PR para `homologacao`/`master` | Fluxo `feature/*` → `desenvolvimento` → `homologacao` → `master` |
| `build`, `deploy` | *(Passo 2/3)* | Imagem no ECR e deploy no EKS, junto com o `infra/` do serviço |

Status checks obrigatórios em `master` e `homologacao`: `testes`, `qualidade`, `validacao`, `guard`.

## Kubernetes

`k8s/base` (ServiceAccount com IRSA, Deployment, Service `ClusterIP`, HPA, Ingress do ALB) e
`k8s/overlays/{hml,prd}`. O namespace (`oficina-hml`/`oficina-prd`) é compartilhado pelos três
serviços e não é criado aqui. Dentro do cluster: `http://oficina-billing-service:8080`. Variáveis do
banco, das filas e dos segredos entram junto com o `infra/` do serviço (Terraform), na entrega de
infraestrutura do Passo 3.
