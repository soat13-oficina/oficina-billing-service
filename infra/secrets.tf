# Credenciais do Mercado Pago (Checkout Pro, ADR 0007). O Terraform cria SO o segredo vazio: o valor
# e da conta de vendedor de teste do grupo e nunca passa pelo state nem pelo repositorio.
#
# Preencher uma vez por ambiente, com um JSON de duas chaves (a pipeline le as duas):
#   access_token    token da aplicacao no Mercado Pago (APP_USR-... do vendedor de teste)
#   webhook_secret  "assinatura secreta" das notificacoes, no painel de integracoes
# Exemplo: aws secretsmanager put-secret-value --secret-id oficina/<env>/mercadopago
#            --secret-string file://mercadopago.json   (arquivo local, fora do repositorio)
resource "aws_secretsmanager_secret" "mercadopago" {
  name        = "${var.project}/${local.environment}/mercadopago"
  description = "Access token e segredo do webhook do Mercado Pago (${local.environment})"

  # Ambiente de desafio: sem isto, um destroy seguido de apply falharia por ~7 dias com
  # "secret scheduled for deletion".
  recovery_window_in_days = 0
}
