# oficina-billing-service

Billing Service da oficina mecânica — **orçamentos e pagamentos (Mercado Pago)**.
Tech Challenge SOAT 13, Fase 4: um dos três microsserviços que substituem o monólito
`oficina-app`.

> **Status (Passo 3, em andamento):** orçamento e pagamento extraídos do `oficina-app` — participante
> da Saga por SQS, banco próprio, Mercado Pago Checkout Pro (preferência, webhook com `x-signature`,
> estorno), tokens de integração e webhook de decisão. Ainda sem deploy (AWS desligada).

## Responsabilidade

| Faz | Não faz |
|---|---|
| Gera o orçamento a partir do diagnóstico (comando `GerarOrcamento` da Saga), com preços consultados no cadastro de peças | Não abre nem altera ordens de serviço — dono: OS Service |
| Alteração, aprovação e rejeição do orçamento (cliente por JWT, funcionário, webhook de decisão com `X-Webhook-Token`) | Não reserva peças — dono: Execution Service |
| Cobrança no Mercado Pago (Checkout Pro), webhook, estorno | Não acessa o banco de nenhum outro serviço |
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
| `orcamento/infrastructure/catalogo` | `CatalogoPecasRest`: preço e descrição das peças por `POST /internal/pecas-insumos/consulta` (Execution Service) |
| `pagamento` | Cobrança (`pagamentos`), resultado (webhook, simulação, expiração) e o participante da Saga (`SolicitarPagamento`, `EstornarPagamento`) |
| `pagamento/infrastructure/mercadopago` | `GatewayPagamentoMercadoPago` (SDK oficial 3.7.0) e `POST /webhooks/mercadopago` |
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
| `SolicitarPagamento` | `PagamentoSolicitado` (cobrança + link). Repetido, devolve a cobrança vigente |
| `EstornarPagamento` | `PagamentoEstornado` (e o orçamento é cancelado) ou `EstornoFalhou` |

Eventos espontâneos: `OrcamentoAprovado` / `OrcamentoRejeitado` (decisão do cliente, do funcionário
ou do webhook, com o canal), gravados na mesma transação da decisão. `PagamentoConfirmado` / `PagamentoRecusado`
(webhook do Mercado Pago) e `PagamentoExpirado` (expirador, a cada 30 s).

Mensagem fora do contrato vai direto para a DLQ; falha de processamento segura as mensagens seguintes
da mesma OS até o visibility timeout (5 tentativas → DLQ). Sem SQS habilitado nada sai do outbox.

## Mercado Pago (Checkout Pro)

Conforme o [ADR 0007](https://github.com/soat13-oficina/oficina-app/blob/desenvolvimento/docs/adr/0007-mercado-pago-checkout-pro.md),
com o SDK oficial `com.mercadopago:sdk-java` 3.7.0:

1. `SolicitarPagamento` cria a cobrança (`PENDENTE`) e uma **preferência** com `external_reference` = id
   da cobrança, `notification_url` e expiração igual ao prazo da Saga. O link (`init_point`, ou
   `sandbox_init_point` em `MERCADOPAGO_SANDBOX=true`) vai no `PagamentoSolicitado` e em
   `GET /orcamentos/{n}/pagamento`.
2. O Mercado Pago chama `POST /webhooks/mercadopago`. A assinatura `x-signature` é validada pelo SDK
   (HMAC-SHA256 sobre `id:{data.id};request-id:{x-request-id};ts:{ts};`, tolerância de 5 min) e o
   pagamento é **consultado na API** — o corpo do webhook nunca é usado como resultado.
   `approved` → `PagamentoConfirmado`; `rejected`/`cancelled` → `PagamentoRecusado`; o resto aguarda.
3. O expirador, antes de marcar `EXPIRADO`, busca pagamentos pela `external_reference`: um aprovado
   cujo webhook atrasou é registrado como aprovado.
4. `EstornarPagamento` → `POST /v1/payments/{id}/refunds` (estorno total) → `PagamentoEstornado`;
   recusa (4xx) → `EstornoFalhou`; falha transitória (5xx, 429, rede) → o comando volta para a fila.
5. Toda criação leva `X-Idempotency-Key`: a da cobrança (`pagamentos.idempotency_key`) na preferência
   e uma derivada dela no estorno. O SDK 3.7.0 gera a própria chave além da nossa (achado do spike):
   `ChaveDeIdempotenciaUnica` remove a gerada.

Desenvolvimento local sem credenciais: `PAGAMENTO_GATEWAY=simulado` — link fictício e
`POST /pagamentos/{id}/simulacao` (`APROVADO`/`RECUSADO`) no lugar do checkout e do webhook.

Credenciais de teste (vendedor de teste, `APP_USR-…`) e o segredo do webhook ficam no Secrets
Manager (`oficina/<env>/mercadopago`) e nos Secrets do GitHub — **nunca** no repositório nem no chat.

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
- `POST /webhooks/mercadopago`: sem JWT, autenticado pela assinatura `x-signature` do Mercado Pago
  (`MERCADOPAGO_WEBHOOK_SECRET`; vazio = nenhuma notificação aceita).
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
| `PAGAMENTO_GATEWAY` | `mercadopago` | `simulado` no desenvolvimento local, sem credenciais |
| `MERCADOPAGO_ACCESS_TOKEN` | vazio (obrigatório com `mercadopago`) | Token da aplicação no Mercado Pago |
| `MERCADOPAGO_WEBHOOK_SECRET` | vazio | Segredo da assinatura do webhook |
| `MERCADOPAGO_NOTIFICATION_URL` | vazio | `https://<gateway>/webhooks/mercadopago`, enviado em cada preferência |
| `MERCADOPAGO_SANDBOX` | `true` | Link de pagamento do sandbox |

## Rodando localmente

```sh
docker-compose up -d postgres              # Postgres do Billing na porta 5434
SERVER_PORT=8081 PAGAMENTO_GATEWAY=simulado ./mvnw spring-boot:run   # 8080 = OS Service
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
- Mercado Pago: o adaptador e o webhook rodam com o SDK de verdade e o HTTP trocado por um dublê
  (`MercadoPagoHttpFalso`) — nenhum teste chama o Mercado Pago.
- **Gate do JaCoCo: mínimo de 80% de linhas e de branches no serviço inteiro** — o build falha
  abaixo disso. Relatório em `target/site/jacoco/index.html` e como artefato `jacoco-report` no CI.

## CI/CD

| Job | Quando | O que faz |
|---|---|---|
| `testes` | PR e push | Build, testes e gate de cobertura |
| `qualidade` | PR e push | SonarCloud com quality gate bloqueante (exige o secret `SONAR_TOKEN`) |
| `validacao` | PR e push | `kubectl kustomize` dos overlays `hml` e `prd` |
| `guard` | PR para `homologacao`/`master` | Fluxo `feature/*` → `desenvolvimento` → `homologacao` → `master` |
| `build`, `deploy` | push em `homologacao`/`master` ou dispatch | Imagem no ECR `oficina-billing-service-<env>` e deploy no EKS: lê banco, JWT, service token e Mercado Pago do Secrets Manager (sem o segredo do MP, `hml` sobe com o gateway simulado e `prd` não sobe) |

O workflow **Infra (Billing)** (`infra.yml`) roda `terraform fmt`/`validate` em PR e push; `plan`,
`apply` e `destroy` só manualmente — ver [`infra/README.md`](infra/README.md).

Status checks obrigatórios em `master` e `homologacao`: `testes`, `qualidade`, `validacao`, `guard`.

## Kubernetes

`k8s/base` (ServiceAccount com IRSA, Deployment, Service `ClusterIP`, HPA, Ingress do ALB) e
`k8s/overlays/{hml,prd}`. O namespace (`oficina-hml`/`oficina-prd`) é compartilhado pelos três
serviços e não é criado aqui. Dentro do cluster: `http://oficina-billing-service:8080`.

`app.env` liga o SQS (`MENSAGERIA_SQS_HABILITADO=true`, credenciais pela Role IRSA) e aponta o
catálogo de peças para o Service `oficina-execution-service:8080` do mesmo namespace. A pipeline acrescenta `DB_URL`,
`PAGAMENTO_GATEWAY`, `MERCADOPAGO_NOTIFICATION_URL` (variable do Environment) e `DD_VERSION`, e cria
os Secrets `oficina-billing-service-secrets` (JWT, service token, Mercado Pago) e
`oficina-billing-db-credentials`.
