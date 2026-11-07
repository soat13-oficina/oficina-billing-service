output "environment" {
  description = "Ambiente correspondente ao workspace selecionado (hml ou prd)."
  value       = local.environment
}

output "irsa_role_arn" {
  description = "Role do pod (anotacao eks.amazonaws.com/role-arn da ServiceAccount)."
  value       = aws_iam_role.servico.arn
}

output "ecr_repository_url" {
  description = "Repositorio das imagens do Billing neste ambiente."
  value       = aws_ecr_repository.servico.repository_url
}

output "db_instance_identifier" {
  description = "Identificador do RDS do Billing - contrato com a pipeline."
  value       = aws_db_instance.billing.identifier
}

output "jdbc_url" {
  description = "URL JDBC do banco do Billing (credenciais no Secrets Manager, gerenciadas pelo RDS)."
  value       = "jdbc:postgresql://${aws_db_instance.billing.address}:${aws_db_instance.billing.port}/${aws_db_instance.billing.db_name}"
}

output "fila_billing_commands" {
  description = "URL da fila que o Billing consome."
  value       = aws_sqs_queue.billing_commands.url
}

output "fila_billing_commands_dlq" {
  description = "URL da DLQ dos comandos do Billing."
  value       = aws_sqs_queue.billing_commands_dlq.url
}

output "mercadopago_secret_name" {
  description = "Segredo a preencher com access_token e webhook_secret (ver secrets.tf)."
  value       = aws_secretsmanager_secret.mercadopago.name
}
