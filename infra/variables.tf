variable "project" {
  description = "Nome do projeto, usado em tags e como prefixo de recursos."
  type        = string
  default     = "oficina"
}

variable "aws_region" {
  description = "Regiao AWS. Precisa ser a mesma da plataforma (oficina-infra-k8s)."
  type        = string
  default     = "us-east-1"
}

# A MESMA bucket do backend deste repositorio. O backend parcial nao expoe seu valor como
# variavel, por isso ele precisa ser informado explicitamente (-var, TF_VAR_state_bucket ou
# terraform.tfvars) para o terraform_remote_state.
variable "state_bucket" {
  description = "Bucket S3 com o state da plataforma, lido aqui via terraform_remote_state."
  type        = string
}

variable "platform_state_key" {
  description = "Key do state da plataforma. Deve casar com o backend de oficina-infra-k8s."
  type        = string
  default     = "oficina/infra-k8s.tfstate"
}

variable "servico" {
  description = "Nome logico do servico; compoe o nome da Role IRSA e do repositorio ECR."
  type        = string
  default     = "oficina-billing-service"
}

variable "service_account" {
  description = "ServiceAccount do Kubernetes que assume a Role via IRSA (k8s/base/serviceaccount.yaml)."
  type        = string
  default     = "oficina-billing-service"
}

variable "db_name" {
  description = "Nome do database criado na instancia."
  type        = string
  default     = "oficina_billing"
}

variable "db_username" {
  description = "Usuario master do PostgreSQL. A senha e gerada e guardada pela AWS no Secrets Manager - nunca passa pelo state."
  type        = string
  default     = "oficina_billing"
}

# 16: a mesma versao dos testes (Testcontainers postgres:16-alpine).
variable "engine_version" {
  description = "Versao major do PostgreSQL."
  type        = string
  default     = "16"
}

# Rode "terraform workspace select hml" (ou prd) antes do plan/apply - o workspace "default" e
# rejeitado de proposito, ver locals.tf.
variable "environments" {
  description = "Configuracao por ambiente, indexada pelo nome do workspace do Terraform."
  type = map(object({
    instance_class              = string
    allocated_storage           = number
    max_allocated_storage       = number
    backup_retention_period     = number
    deletion_protection         = bool
    multi_az                    = bool
    skip_final_snapshot         = bool
    retencao_mensagens_segundos = number
    retencao_dlq_segundos       = number
  }))

  default = {
    hml = {
      instance_class        = "db.t3.micro"
      allocated_storage     = 20
      max_allocated_storage = 50
      # Homologacao nao guarda dado que precise ser recuperado.
      backup_retention_period     = 0
      deletion_protection         = false
      multi_az                    = false
      skip_final_snapshot         = true
      retencao_mensagens_segundos = 345600 # 4 dias
      retencao_dlq_segundos       = 604800 # 7 dias
    }
    prd = {
      instance_class          = "db.t3.micro"
      allocated_storage       = 20
      max_allocated_storage   = 100
      backup_retention_period = 7
      # false (e nao true) porque o ambiente do desafio e destruido ao fim de cada demonstracao,
      # como o RDS do OS em oficina-infra-database.
      deletion_protection         = false
      multi_az                    = false
      skip_final_snapshot         = true
      retencao_mensagens_segundos = 345600  # 4 dias
      retencao_dlq_segundos       = 1209600 # 14 dias (maximo do SQS)
    }
  }
}
