# Depende so da plataforma (oficina-infra-k8s): rede, subnet group de banco, security group dos
# nodes do EKS e o OIDC provider para a Role IRSA.
#
# Se este data source falhar com "Unable to find remote state", a plataforma ainda nao foi
# provisionada naquele ambiente: rode o apply de oficina-infra-k8s primeiro.
data "terraform_remote_state" "platform" {
  backend = "s3"

  config = {
    bucket = var.state_bucket
    key    = var.platform_state_key
    region = var.aws_region
  }
}

data "aws_caller_identity" "atual" {}

locals {
  platform                   = data.terraform_remote_state.platform.outputs
  vpc_id                     = local.platform.vpc_id
  database_subnet_group_name = local.platform.database_subnet_group_name
  node_security_group_id     = local.platform.node_security_group_id
  conta_id                   = data.aws_caller_identity.atual.account_id
}
