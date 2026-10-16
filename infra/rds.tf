# Banco proprio do Billing (orcamentos, pagamentos, tokens de integracao, outbox) - nenhum
# servico acessa o banco de outro (ADR 0003). Mesmo desenho do RDS do OS em oficina-infra-database.

resource "aws_security_group" "rds" {
  name_prefix = "${local.db_identifier}-"
  description = "Acesso ao PostgreSQL do Billing (${local.environment}) apenas a partir dos nodes do EKS"
  vpc_id      = local.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

# Sem egress e sem ingress por CIDR: o unico caminho ate a 5432 e o security group dos nodes.
resource "aws_vpc_security_group_ingress_rule" "from_eks_nodes" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL a partir dos worker nodes do EKS"
  referenced_security_group_id = local.node_security_group_id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_db_instance" "billing" {
  # Identificador previsivel: contrato com a pipeline deste repositorio, que descobre endpoint e
  # credenciais com aws rds describe-db-instances --db-instance-identifier oficina-billing-db-<env>.
  identifier = local.db_identifier

  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = local.cfg.instance_class

  allocated_storage     = local.cfg.allocated_storage
  max_allocated_storage = local.cfg.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  # Senha gerada e guardada pela AWS no Secrets Manager - nunca passa pelo state nem pelo repo.
  manage_master_user_password = true

  db_subnet_group_name   = local.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az                = local.cfg.multi_az
  backup_retention_period = local.cfg.backup_retention_period
  deletion_protection     = local.cfg.deletion_protection
  skip_final_snapshot     = local.cfg.skip_final_snapshot
  final_snapshot_identifier = (
    local.cfg.skip_final_snapshot
    ? null
    : "${local.db_identifier}-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  )

  maintenance_window = "Mon:04:00-Mon:05:00"
  backup_window      = local.cfg.backup_retention_period > 0 ? "03:00-03:45" : null

  enabled_cloudwatch_logs_exports       = ["postgresql", "upgrade"]
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  auto_minor_version_upgrade = true
  apply_immediately          = true

  lifecycle {
    # timestamp() muda a cada plan; sem isto, todo plan mostraria diff no snapshot final.
    ignore_changes = [final_snapshot_identifier]
  }
}
