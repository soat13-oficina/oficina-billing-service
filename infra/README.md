# infra/ — Billing Service

Terraform do que é **só do Billing** (Fase 4, "cada serviço com sua infraestrutura" —
`PLANO-FASE-4.md` do grupo, seção 9.1). A plataforma (VPC, EKS) vem de `oficina-infra-k8s`.

| Recurso | Nome | Para quê |
|---|---|---|
| RDS PostgreSQL 16 + security group | `oficina-billing-db-<env>` | Banco próprio (orçamentos, pagamentos, tokens de integração, outbox). Só os nodes do EKS chegam na 5432; usuário e senha gerenciados pelo RDS no Secrets Manager |
| Fila SQS FIFO + DLQ | `oficina-<env>-billing-commands.fifo` / `-dlq.fifo` | Comandos da Saga para o Billing. Visibilidade de 60 s, long polling de 20 s, 5 recebimentos antes da DLQ |
| ECR | `oficina-billing-service-<env>` | Imagens do serviço — um por ambiente, para o destroy de `hml` não levar as de `prd` |
| IAM Role (IRSA) | `oficina-<env>-oficina-billing-service` | Pod do Billing: consumir `billing-commands`, mandar para a própria DLQ e responder em `os-saga-replies` (ARN pelo nome). Confia só na ServiceAccount `oficina-billing-service` do namespace `oficina-<env>` |
| Segredo (vazio) | `oficina/<env>/mercadopago` | `access_token` e `webhook_secret` do Mercado Pago — **preenchido pelo grupo**, nunca pelo Terraform |

## Preencher o segredo do Mercado Pago

Uma vez por ambiente, com as credenciais do **vendedor de teste** e a assinatura secreta do webhook
(painel de integrações do Mercado Pago). O valor não passa pelo state, pelo repositório nem pelo chat:

```sh
# mercadopago.json (local, fora do repositório): {"access_token": "...", "webhook_secret": "..."}
aws secretsmanager put-secret-value --secret-id oficina/hml/mercadopago \
  --secret-string file://mercadopago.json
```

Sem ele o deploy de `hml` sobe com o gateway simulado e o de `prd` falha.

## Como aplicar

**Só manualmente** — workflow **Infra (Billing)** (`.github/workflows/infra.yml`) → *Run
workflow* → ambiente e ação (`plan`, `apply`, `destroy`). Em PR e push ele só roda `fmt` e
`validate`, sem AWS.

Ordem: `oficina-infra-k8s` → `oficina-infra-database` → `infra/` do OS Service (cria
`os-saga-replies` e o `service-token`) → **este** → deploy dos serviços → `oficina-lambda-auth`.
Destroy na ordem inversa.

Localmente:

```sh
cd infra
cp backend.hcl.example backend.hcl            # bucket dos states (nao versionado)
cp terraform.tfvars.example terraform.tfvars  # a mesma bucket, para o remote state da plataforma
terraform init -backend-config=backend.hcl
terraform workspace select -or-create hml
terraform plan
```

## Contratos com outros repositórios

- **Pipeline deste repo** (`ci-cd.yml`): `oficina-billing-db-<env>`, a Role
  `oficina-<env>-oficina-billing-service`, o ECR `oficina-billing-service-<env>`,
  `oficina/<env>/mercadopago`, além de `oficina/<env>/service-token` e `oficina/<env>/jwt-secret`
  (os dois do `infra/` do OS).
- **OS Service** (`oficina-app/infra`): cria `os-saga-replies` e o `service-token`; manda os comandos
  para `billing-commands` pelo nome.
- **Plataforma** (`oficina-infra-k8s`): outputs `vpc_id`, `database_subnet_group_name`,
  `node_security_group_id` e `oidc_provider_arn`.
