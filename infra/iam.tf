# Role do pod do Billing via IRSA: consumir billing-commands e responder em os-saga-replies.
#
# Nome previsivel - contrato com a pipeline (e com k8s/overlays/*/patch-serviceaccount.yaml):
#   aws iam get-role --role-name oficina-<env>-oficina-billing-service

data "aws_iam_policy_document" "trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.platform.oidc_provider_arn]
    }

    # So a ServiceAccount deste servico, no namespace deste ambiente.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider}:sub"
      values   = ["system:serviceaccount:${local.namespace}:${var.service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "servico" {
  name               = "${var.project}-${local.environment}-${var.servico}"
  description        = "Billing Service (${local.environment}): SQS da Saga, assumida via IRSA"
  assume_role_policy = data.aws_iam_policy_document.trust.json
}

data "aws_iam_policy_document" "servico" {
  statement {
    sid = "ConsomeComandosDaSaga"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.billing_commands.arn]
  }

  # O ReceptorSqs manda direto para a DLQ o que esta fora do contrato.
  statement {
    sid       = "EnviaParaAPropriaDlq"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
    resources = [aws_sqs_queue.billing_commands_dlq.arn]
  }

  # os-saga-replies e criada pelo infra/ do OS Service: ARN pelo nome, sem ler o state de la.
  statement {
    sid       = "RespondeAoOrquestrador"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
    resources = ["arn:aws:sqs:${var.aws_region}:${local.conta_id}:${local.prefixo_filas}-os-saga-replies.fifo"]
  }
}

resource "aws_iam_role_policy" "servico" {
  name   = "${var.project}-${local.environment}-${var.servico}"
  role   = aws_iam_role.servico.id
  policy = data.aws_iam_policy_document.servico.json
}
