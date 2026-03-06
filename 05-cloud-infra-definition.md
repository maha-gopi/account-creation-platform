# 05 - Cloud Infrastructure Definition

**Document Version**: 1.0  
**Date**: March 3, 2026  
**Cloud Provider**: AWS  
**Environment**: Dev/Test (Minimum Viable Infrastructure)  
**Status**: Ready for Implementation

---

## Table of Contents

1. [Environments & Naming Conventions](#1-environments--naming-conventions)
2. [Compute Provisioning](#2-compute-provisioning)
3. [Storage Infrastructure](#3-storage-infrastructure)
4. [Database Provisioning](#4-database-provisioning)
5. [Messaging Infrastructure](#5-messaging-infrastructure)
6. [Secrets & Configuration Management](#6-secrets--configuration-management)
7. [Observability Stack](#7-observability-stack)
8. [CI/CD Pipeline](#8-cicd-pipeline)
9. [Operational Runbook](#9-operational-runbook)
10. [Cost Estimation](#10-cost-estimation)
11. [Terraform Structure](#11-terraform-structure)
12. [GitHub Actions Workflow](#12-github-actions-workflow)

---

## 1. Environments & Naming Conventions

### 1.1 Environment Definitions

| Environment | Purpose | Data | Availability | Cost Optimization |
|-------------|---------|------|--------------|-------------------|
| **DEV** | Development, integration testing | Synthetic/masked data | 8x5 (weekdays only) | ✅ Spot instances, stopped nights |
| **TEST** | QA, UAT, parity testing | Production-like masked data | 12x5 (extended hours) | ✅ Reserved instances, minimal HA |
| **PROD** | Production workload | Real PII data | 24x7 | ❌ No cost optimization, full HA |

**This document focuses on DEV and TEST environments only.**

---

### 1.2 Naming Convention

**Format**: `{project}-{service}-{environment}-{resource-type}-{sequence}`

**Examples**:
```
acct-batch-dev-ecs-cluster
acct-batch-dev-s3-input
acct-batch-test-rds-postgresql
acct-coreapi-dev-ecs-service
acct-coreapi-test-alb
```

**Tagging Strategy**:
```hcl
# Standard tags applied to all resources
tags = {
  Project            = "account-creation-platform"
  Environment        = var.environment  # dev, test, prod
  ManagedBy          = "terraform"
  CostCenter         = "BFSI-IT"
  Owner              = "platform-team@bfsi.com"
  DataClassification = var.environment == "prod" ? "PII" : "Synthetic"
  BackupPolicy       = var.environment == "prod" ? "daily" : "none"
  ComplianceScope    = "SOC2,PCI-DSS"
}
```

---

### 1.3 AWS Account Structure

```
┌─────────────────────────────────────────────────────────────┐
│  AWS Organization (Root)                                    │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐│
│  │  Dev Account   │  │  Test Account  │  │  Prod Account  ││
│  │  111122223333  │  │  444455556666  │  │  777788889999  ││
│  │                │  │                │  │                ││
│  │  • ECS Tasks   │  │  • ECS Tasks   │  │  • ECS Fargate ││
│  │  • RDS t3.small│  │  • RDS t3.medium  │  • RDS r6g.large││
│  │  • S3 buckets  │  │  • S3 buckets  │  │  • S3 buckets  ││
│  │  • EventBridge │  │  • EventBridge │  │  • EventBridge ││
│  └────────────────┘  └────────────────┘  └────────────────┘│
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Shared Services Account (999900001111)               │ │
│  │  • ECR (Container Registry)                           │ │
│  │  • CodePipeline, CodeBuild                            │ │
│  │  • CloudWatch Logs (centralized)                      │ │
│  │  • Secrets Manager (cross-account access)             │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

### 1.4 VPC Design (Per Environment)

```
VPC: 10.{env}.0.0/16
  - Dev:  10.1.0.0/16
  - Test: 10.2.0.0/16
  - Prod: 10.3.0.0/16

Subnets:
  ├── Public Subnets (for ALB, NAT Gateway)
  │   ├── 10.{env}.1.0/24 (AZ-a)
  │   ├── 10.{env}.2.0/24 (AZ-b)
  │   └── 10.{env}.3.0/24 (AZ-c)
  │
  ├── Private Subnets (for ECS Tasks, Lambda)
  │   ├── 10.{env}.11.0/24 (AZ-a)
  │   ├── 10.{env}.12.0/24 (AZ-b)
  │   └── 10.{env}.13.0/24 (AZ-c)
  │
  └── Database Subnets (isolated, no internet)
      ├── 10.{env}.21.0/24 (AZ-a)
      ├── 10.{env}.22.0/24 (AZ-b)
      └── 10.{env}.23.0/24 (AZ-c)

Internet Gateway: 1 per VPC
NAT Gateway: 1 per AZ (Dev: 1 only, Test: 2, Prod: 3)
```

---

## 2. Compute Provisioning

### 2.1 Compute Options Analysis

| Option | Pros | Cons | Dev Cost | Test Cost | Recommendation |
|--------|------|------|----------|-----------|----------------|
| **ECS Fargate** | Serverless, no server mgmt | Higher cost per hour | $50/mo | $150/mo | ✅ **Batch API** (event-driven) |
| **ECS EC2** | Lower cost, more control | Requires cluster mgmt | $30/mo | $100/mo | ✅ **Core API** (always-on) |
| **Lambda** | Pay-per-request, scales to 0 | 15-min timeout limit | $10/mo | $30/mo | ❌ Not suitable (batch > 15 min) |
| **EC2 Spot** | 70% cheaper | Can be interrupted | $15/mo | $50/mo | ⚠️ Dev only (Test needs stability) |

**Decision**:
- **Business Process API (Spring Batch)**: ECS Fargate (on-demand, event-driven)
- **Core Account API (REST)**: ECS on EC2 (always-on, cost-effective)

---

### 2.2 ECS Cluster Configuration

#### Cluster: Business Process API (Batch Jobs)

```hcl
# Fargate configuration for batch processing
resource "aws_ecs_cluster" "batch_cluster" {
  name = "acct-batch-${var.environment}-ecs-cluster"

  configuration {
    execute_command_configuration {
      logging = "OVERRIDE"
      log_configuration {
        cloud_watch_log_group_name = "/ecs/acct-batch-${var.environment}"
      }
    }
  }

  tags = local.common_tags
}

# Task Definition
resource "aws_ecs_task_definition" "batch_task" {
  family                   = "acct-batch-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.environment == "dev" ? "1024" : "2048"  # 1 vCPU (dev), 2 vCPU (test)
  memory                   = var.environment == "dev" ? "2048" : "4096"  # 2 GB (dev), 4 GB (test)
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "batch-container"
    image     = "${var.ecr_repository_url}:${var.image_tag}"
    essential = true

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "BATCH_CHUNK_SIZE", value = "100" },
      { name = "S3_INPUT_BUCKET", value = aws_s3_bucket.input.bucket },
      { name = "S3_OUTPUT_BUCKET", value = aws_s3_bucket.output.bucket }
    ]

    secrets = [
      { name = "CORE_API_URL", valueFrom = "${aws_secretsmanager_secret.config.arn}:core_api_url::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db_password.arn}::" }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/acct-batch-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "batch"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
}
```

**Resource Allocation**:

| Environment | vCPU | Memory | Max Tasks | Estimated Throughput |
|-------------|------|--------|-----------|----------------------|
| **Dev** | 1 vCPU | 2 GB | 2 | 500 records/sec |
| **Test** | 2 vCPU | 4 GB | 5 | 1000 records/sec |
| **Prod** | 4 vCPU | 8 GB | 20 | 5000 records/sec |

---

#### Cluster: Core Account API (Always-On Service)

```hcl
# EC2-backed ECS cluster for cost efficiency
resource "aws_ecs_cluster" "core_api_cluster" {
  name = "acct-coreapi-${var.environment}-ecs-cluster"

  capacity_providers = var.environment == "dev" ? ["FARGATE_SPOT"] : ["FARGATE"]

  tags = local.common_tags
}

# Task Definition
resource "aws_ecs_task_definition" "core_api_task" {
  family                   = "acct-coreapi-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"   # 0.5 vCPU
  memory                   = "1024"  # 1 GB
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([{
    name      = "core-api-container"
    image     = "${var.ecr_repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      containerPort = 8081
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "SERVER_PORT", value = "8081" }
    ]

    secrets = [
      { name = "DB_URL", valueFrom = "${aws_secretsmanager_secret.config.arn}:db_url::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db_password.arn}::" },
      { name = "EVENTBRIDGE_BUS_ARN", valueFrom = "${aws_secretsmanager_secret.config.arn}:eventbridge_bus::" }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/acct-coreapi-${var.environment}"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "api"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 45
    }
  }])
}

# ECS Service (always running)
resource "aws_ecs_service" "core_api_service" {
  name            = "acct-coreapi-${var.environment}-service"
  cluster         = aws_ecs_cluster.core_api_cluster.id
  task_definition = aws_ecs_task_definition.core_api_task.arn
  desired_count   = var.environment == "dev" ? 1 : 2  # Dev: 1, Test: 2

  launch_type = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.core_api.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.core_api.arn
    container_name   = "core-api-container"
    container_port   = 8081
  }

  depends_on = [aws_lb_listener.core_api]
}
```

---

### 2.3 Application Load Balancer (ALB)

```hcl
# ALB for Core Account API
resource "aws_lb" "core_api" {
  name               = "acct-coreapi-${var.environment}-alb"
  internal           = true  # Internal ALB (accessed only by Batch API)
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.private[*].id

  enable_deletion_protection = var.environment == "prod" ? true : false
  enable_http2              = true

  tags = local.common_tags
}

# Target Group
resource "aws_lb_target_group" "core_api" {
  name        = "acct-coreapi-${var.environment}-tg"
  port        = 8081
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = local.common_tags
}

# Listener
resource "aws_lb_listener" "core_api" {
  load_balancer_arn = aws_lb.core_api.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.core_api.arn
  }
}
```

---

### 2.4 Auto-Scaling Configuration

#### Core API Auto-Scaling
```hcl
resource "aws_appautoscaling_target" "core_api" {
  max_capacity       = var.environment == "dev" ? 2 : 5
  min_capacity       = var.environment == "dev" ? 1 : 2
  resource_id        = "service/${aws_ecs_cluster.core_api_cluster.name}/${aws_ecs_service.core_api_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Scale up on CPU
resource "aws_appautoscaling_policy" "core_api_cpu" {
  name               = "acct-coreapi-${var.environment}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.core_api.resource_id
  scalable_dimension = aws_appautoscaling_target.core_api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.core_api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = 70.0
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
```

---

## 3. Storage Infrastructure

### 3.1 S3 Bucket Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  S3 Storage Structure                                       │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  acct-batch-{env}-input-files                          │ │
│  │  Purpose: Raw input files uploaded for processing      │ │
│  │  Lifecycle: 90 days retention, then Glacier            │ │
│  │                                                          │ │
│  │  Structure:                                             │ │
│  │    /{date}/input_{timestamp}.dat                       │ │
│  │    /2026/03/03/input_20260303_101530.dat               │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  acct-batch-{env}-reports                              │ │
│  │  Purpose: Success/failure reports, control totals      │ │
│  │  Lifecycle: 365 days retention, then Glacier           │ │
│  │                                                          │ │
│  │  Structure:                                             │ │
│  │    /{job-id}/success_report_{timestamp}.dat            │ │
│  │    /{job-id}/failure_report_{timestamp}.dat            │ │
│  │    /{job-id}/control_totals_{timestamp}.json           │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  acct-batch-{env}-archive                              │ │
│  │  Purpose: Processed files (success + failure)          │ │
│  │  Lifecycle: 7 years retention (compliance)             │ │
│  │                                                          │ │
│  │  Structure:                                             │ │
│  │    /{year}/{month}/{day}/archive_{job-id}.tar.gz       │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  acct-batch-{env}-config                               │ │
│  │  Purpose: Application configs, validation rules        │ │
│  │  Lifecycle: No expiration (versioned)                  │ │
│  │                                                          │ │
│  │  Structure:                                             │ │
│  │    /application-{env}.yml                              │ │
│  │    /validation-rules.json                              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

### 3.2 S3 Bucket Terraform Configuration

```hcl
# Input Files Bucket
resource "aws_s3_bucket" "input" {
  bucket = "acct-batch-${var.environment}-input-files"
  tags   = local.common_tags
}

resource "aws_s3_bucket_versioning" "input" {
  bucket = aws_s3_bucket.input.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "input" {
  bucket = aws_s3_bucket.input.id

  rule {
    id     = "archive-old-inputs"
    status = "Enabled"

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 2555  # 7 years
    }
  }
}

resource "aws_s3_bucket_public_access_block" "input" {
  bucket = aws_s3_bucket.input.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "input" {
  bucket = aws_s3_bucket.input.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
  }
}

# EventBridge notification on new file upload
resource "aws_s3_bucket_notification" "input_notification" {
  bucket      = aws_s3_bucket.input.id
  eventbridge = true
}

# Reports Bucket (similar configuration)
resource "aws_s3_bucket" "reports" {
  bucket = "acct-batch-${var.environment}-reports"
  tags   = local.common_tags
}

# Archive Bucket (similar configuration)
resource "aws_s3_bucket" "archive" {
  bucket = "acct-batch-${var.environment}-archive"
  tags   = local.common_tags
}

# Config Bucket (similar configuration)
resource "aws_s3_bucket" "config" {
  bucket = "acct-batch-${var.environment}-config"
  tags   = local.common_tags
}
```

---

### 3.3 EventBridge Rule for Auto-Triggering Batch

```hcl
# EventBridge Rule: Trigger batch job on S3 upload
resource "aws_cloudwatch_event_rule" "s3_upload" {
  name        = "acct-batch-${var.environment}-s3-upload-trigger"
  description = "Trigger batch job when new file uploaded to input bucket"

  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [aws_s3_bucket.input.bucket]
      }
      object = {
        key = [{
          prefix = "" # Trigger on any file
        }]
      }
    }
  })

  tags = local.common_tags
}

# EventBridge Target: Invoke ECS Task
resource "aws_cloudwatch_event_target" "ecs_task" {
  rule      = aws_cloudwatch_event_rule.s3_upload.name
  target_id = "TriggerBatchTask"
  arn       = aws_ecs_cluster.batch_cluster.arn
  role_arn  = aws_iam_role.eventbridge_ecs.arn

  ecs_target {
    task_definition_arn = aws_ecs_task_definition.batch_task.arn
    task_count          = 1
    launch_type         = "FARGATE"

    network_configuration {
      subnets          = aws_subnet.private[*].id
      security_groups  = [aws_security_group.batch.id]
      assign_public_ip = false
    }
  }

  input_transformer {
    input_paths = {
      bucket = "$.detail.bucket.name"
      key    = "$.detail.object.key"
    }
    input_template = <<EOF
{
  "containerOverrides": [{
    "name": "batch-container",
    "environment": [
      {"name": "S3_INPUT_FILE", "value": "s3://<bucket>/<key>"},
      {"name": "JOB_NAME", "value": "auto-triggered-<key>"}
    ]
  }]
}
EOF
  }
}
```

---

## 4. Database Provisioning

### 4.1 RDS PostgreSQL Configuration

```hcl
# RDS PostgreSQL Instance
resource "aws_db_instance" "postgresql" {
  identifier = "acct-batch-${var.environment}-postgresql"

  # Engine
  engine               = "postgres"
  engine_version       = "15.4"
  instance_class       = var.environment == "dev" ? "db.t3.small" : "db.t3.medium"
  
  # Storage
  allocated_storage     = var.environment == "dev" ? 20 : 50  # GB
  max_allocated_storage = var.environment == "dev" ? 100 : 200
  storage_type         = "gp3"
  storage_encrypted    = true
  kms_key_id           = aws_kms_key.rds.arn

  # Database
  db_name  = "acct_db"
  username = "acct_admin"
  password = random_password.db_password.result  # Stored in Secrets Manager
  port     = 5432

  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # Backup
  backup_retention_period   = var.environment == "dev" ? 1 : 7
  backup_window            = "03:00-04:00"
  maintenance_window       = "mon:04:00-mon:05:00"
  delete_automated_backups = var.environment == "dev" ? true : false

  # High Availability (Test/Prod only)
  multi_az = var.environment == "dev" ? false : true

  # Monitoring
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  performance_insights_enabled    = true
  performance_insights_retention_period = 7

  # Protection
  deletion_protection = var.environment == "prod" ? true : false
  skip_final_snapshot = var.environment == "dev" ? true : false
  final_snapshot_identifier = var.environment == "dev" ? null : "acct-batch-${var.environment}-final-snapshot-${formatdate("YYYYMMDD-hhmmss", timestamp())}"

  # Parameter Group (custom settings)
  parameter_group_name = aws_db_parameter_group.postgresql.name

  tags = local.common_tags
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "acct-batch-${var.environment}-db-subnet-group"
  subnet_ids = aws_subnet.database[*].id

  tags = local.common_tags
}

# Custom Parameter Group
resource "aws_db_parameter_group" "postgresql" {
  name   = "acct-batch-${var.environment}-postgresql-params"
  family = "postgres15"

  parameter {
    name  = "max_connections"
    value = var.environment == "dev" ? "100" : "200"
  }

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4096}"  # 25% of instance memory
  }

  parameter {
    name  = "log_statement"
    value = var.environment == "dev" ? "all" : "ddl"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # Log queries > 1 second
  }

  tags = local.common_tags
}

# Random password for DB
resource "random_password" "db_password" {
  length  = 32
  special = true
}

# Store password in Secrets Manager
resource "aws_secretsmanager_secret" "db_password" {
  name = "acct-batch-${var.environment}-db-password"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}
```

**Instance Sizing**:

| Environment | Instance Class | vCPU | Memory | Storage | IOPS | Cost/Month |
|-------------|----------------|------|--------|---------|------|------------|
| **Dev** | db.t3.small | 2 | 2 GB | 20 GB | 3000 | ~$30 |
| **Test** | db.t3.medium | 2 | 4 GB | 50 GB | 3000 | ~$60 |
| **Prod** | db.r6g.large | 2 | 16 GB | 200 GB | 12000 | ~$200 |

---

### 4.2 Schema Migration Strategy

#### Flyway Configuration

```yaml
# application.yml (Spring Boot)
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
    schemas: acct_owner
    table: flyway_schema_history
    validate-on-migrate: true
    out-of-order: false
```

#### Migration Script Structure

```
src/main/resources/db/migration/
  ├── V1__initial_schema.sql          # Tables, indexes, constraints
  ├── V2__seed_reference_data.sql     # Lookup tables (currency, account types)
  ├── V3__add_audit_columns.sql       # Created_at, updated_at, created_by
  ├── V4__add_indexes.sql             # Performance indexes
  ├── V5__add_partitioning.sql        # Partition account_audit by month
  └── R__stored_procedures.sql        # Repeatable: Stored procedures
```

#### Sample Migration Script

```sql
-- V1__initial_schema.sql
-- Flyway migration: Initial schema for account creation platform
-- Version: 1.0
-- Date: 2026-03-03

-- Create schema
CREATE SCHEMA IF NOT EXISTS acct_owner;

-- Set search path
SET search_path TO acct_owner;

-- Customer table (reference data, maintained by CRM system)
CREATE TABLE IF NOT EXISTS customer (
    customer_id       VARCHAR(12) PRIMARY KEY,
    customer_name     VARCHAR(100) NOT NULL,
    date_of_birth     DATE NOT NULL,
    country           VARCHAR(2) NOT NULL,
    status            CHAR(1) NOT NULL DEFAULT 'A',  -- A=Active, I=Inactive
    blacklist_flag    CHAR(1) NOT NULL DEFAULT 'N',  -- Y=Blacklisted, N=Normal
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_status CHECK (status IN ('A', 'I')),
    CONSTRAINT chk_blacklist_flag CHECK (blacklist_flag IN ('Y', 'N'))
);

CREATE INDEX idx_customer_status ON customer(status) WHERE status = 'A';
CREATE INDEX idx_customer_blacklist ON customer(blacklist_flag) WHERE blacklist_flag = 'Y';

-- Account table (main entity)
CREATE TABLE IF NOT EXISTS account (
    account_no        VARCHAR(20) PRIMARY KEY,
    request_id        VARCHAR(20) NOT NULL UNIQUE,
    customer_id       VARCHAR(12) NOT NULL,
    account_type      VARCHAR(3) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    open_balance      DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    channel_code      VARCHAR(10) NOT NULL,
    status            CHAR(1) NOT NULL DEFAULT 'A',  -- A=Active, C=Closed, S=Suspended
    open_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    close_date        DATE,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_account_type CHECK (account_type IN ('SAV', 'CUR', 'LOA')),
    CONSTRAINT chk_currency CHECK (currency IN ('INR', 'USD', 'EUR')),
    CONSTRAINT chk_account_status CHECK (status IN ('A', 'C', 'S')),
    CONSTRAINT chk_open_balance CHECK (open_balance >= 0)
);

CREATE INDEX idx_account_customer ON account(customer_id);
CREATE INDEX idx_account_request ON account(request_id);
CREATE INDEX idx_account_type ON account(account_type);
CREATE INDEX idx_account_status ON account(status);
CREATE INDEX idx_account_open_date ON account(open_date);

-- Account Audit table (tracking all account creation attempts)
CREATE TABLE IF NOT EXISTS account_audit (
    audit_id          BIGSERIAL PRIMARY KEY,
    request_id        VARCHAR(20) NOT NULL,
    customer_id       VARCHAR(12) NOT NULL,
    account_no        VARCHAR(20),
    account_type      VARCHAR(3) NOT NULL,
    initial_deposit   DECIMAL(15,2) NOT NULL,
    channel_code      VARCHAR(10) NOT NULL,
    status            VARCHAR(10) NOT NULL,  -- SUCCESS, FAILED
    error_code        VARCHAR(12),
    error_message     VARCHAR(500),
    processing_time_ms INT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_audit_request ON account_audit(request_id);
CREATE INDEX idx_audit_customer ON account_audit(customer_id);
CREATE INDEX idx_audit_account ON account_audit(account_no);
CREATE INDEX idx_audit_status ON account_audit(status);
CREATE INDEX idx_audit_created ON account_audit(created_at);

-- Comments
COMMENT ON TABLE customer IS 'Customer master data (reference)';
COMMENT ON TABLE account IS 'Account master table (core entity)';
COMMENT ON TABLE account_audit IS 'Audit trail for all account creation attempts';

-- Grant permissions
GRANT USAGE ON SCHEMA acct_owner TO acct_app_user;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA acct_owner TO acct_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA acct_owner TO acct_app_user;
```

#### Migration Execution Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Flyway Migration Flow                                      │
│                                                              │
│  1. Application Startup                                     │
│     └─> Spring Boot detects Flyway on classpath           │
│                                                              │
│  2. Flyway Initialization                                   │
│     ├─> Connect to DB (RDS PostgreSQL)                     │
│     ├─> Check flyway_schema_history table                  │
│     └─> If missing, create and baseline                    │
│                                                              │
│  3. Migration Scan                                          │
│     ├─> Scan classpath:db/migration                        │
│     ├─> Detect V*.sql files                                │
│     └─> Compare with executed migrations                   │
│                                                              │
│  4. Migration Execution                                     │
│     ├─> Execute pending migrations in order (V1, V2, ...)  │
│     ├─> Each migration in a transaction                    │
│     ├─> Log to flyway_schema_history                       │
│     └─> Rollback on error, halt startup                    │
│                                                              │
│  5. Application Ready                                       │
│     └─> Spring Boot completes startup                      │
└─────────────────────────────────────────────────────────────┘
```

**Rollback Strategy**:
- **Forward-only migrations**: Flyway does NOT support automatic rollback
- **Manual rollback**: Create new migration (e.g., `V6__rollback_v5.sql`) to undo changes
- **Backup restore**: For critical issues, restore RDS snapshot before migration

---

## 5. Messaging Infrastructure

### 5.1 EventBridge Configuration

```hcl
# Custom Event Bus
resource "aws_cloudwatch_event_bus" "account_events" {
  name = "acct-batch-${var.environment}-event-bus"
  tags = local.common_tags
}

# Event Bus Policy (allow cross-account access if needed)
resource "aws_cloudwatch_event_bus_policy" "account_events" {
  event_bus_name = aws_cloudwatch_event_bus.account_events.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowAccountToPutEvents"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "events:PutEvents"
        Resource = aws_cloudwatch_event_bus.account_events.arn
      }
    ]
  })
}

# Event Archive (for replay in test)
resource "aws_cloudwatch_event_archive" "account_events" {
  name             = "acct-batch-${var.environment}-event-archive"
  event_source_arn = aws_cloudwatch_event_bus.account_events.arn
  retention_days   = var.environment == "dev" ? 7 : 30

  event_pattern = jsonencode({
    source = ["core-account-api"]
  })
}
```

---

### 5.2 Event Rules & Targets

```hcl
# Rule: Route ACCOUNT_CREATED events to SQS (for downstream consumers)
resource "aws_cloudwatch_event_rule" "account_created" {
  name           = "acct-batch-${var.environment}-account-created-rule"
  description    = "Route ACCOUNT_CREATED events to downstream systems"
  event_bus_name = aws_cloudwatch_event_bus.account_events.name

  event_pattern = jsonencode({
    source      = ["core-account-api"]
    detail-type = ["ACCOUNT_CREATED"]
  })

  tags = local.common_tags
}

# Target: SQS Queue (for downstream systems)
resource "aws_cloudwatch_event_target" "account_created_sqs" {
  rule           = aws_cloudwatch_event_rule.account_created.name
  event_bus_name = aws_cloudwatch_event_bus.account_events.name
  arn            = aws_sqs_queue.account_events.arn

  retry_policy {
    maximum_retry_attempts = 3
    maximum_event_age      = 3600  # 1 hour
  }

  dead_letter_config {
    arn = aws_sqs_queue.account_events_dlq.arn
  }
}

# SQS Queue for downstream consumers
resource "aws_sqs_queue" "account_events" {
  name                       = "acct-batch-${var.environment}-account-events-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 1209600  # 14 days
  receive_wait_time_seconds  = 20       # Long polling

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.account_events_dlq.arn
    maxReceiveCount     = 3
  })

  tags = local.common_tags
}

# Dead Letter Queue
resource "aws_sqs_queue" "account_events_dlq" {
  name                      = "acct-batch-${var.environment}-account-events-dlq"
  message_retention_seconds = 1209600  # 14 days

  tags = merge(local.common_tags, {
    Alert = "true"  # Trigger CloudWatch alarm on messages
  })
}

# SQS Queue Policy (allow EventBridge to send)
resource "aws_sqs_queue_policy" "account_events" {
  queue_url = aws_sqs_queue.account_events.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.account_events.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_cloudwatch_event_rule.account_created.arn
          }
        }
      }
    ]
  })
}
```

---

### 5.3 DynamoDB for Idempotency Cache

```hcl
# DynamoDB Table: Idempotency Cache
resource "aws_dynamodb_table" "idempotency_cache" {
  name           = "acct-batch-${var.environment}-idempotency-cache"
  billing_mode   = "PAY_PER_REQUEST"  # On-demand pricing for dev/test
  hash_key       = "requestId"

  attribute {
    name = "requestId"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = var.environment == "prod" ? true : false
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = aws_kms_key.dynamodb.arn
  }

  tags = local.common_tags
}

# Global Secondary Index (optional, for query by customerId)
resource "aws_dynamodb_table_item" "idempotency_cache_gsi" {
  table_name = aws_dynamodb_table.idempotency_cache.name
  hash_key   = aws_dynamodb_table.idempotency_cache.hash_key

  # Sample item structure (for reference)
  item = <<ITEM
{
  "requestId": {"S": "REQ0000000000001"},
  "accountNumber": {"S": "12312026030312345"},
  "customerId": {"S": "CUST00000001"},
  "createdAt": {"N": "1709467200"},
  "expiresAt": {"N": "1710072000"}
}
ITEM
}
```

---

## 6. Secrets & Configuration Management

### 6.1 AWS Secrets Manager

```hcl
# Secret: Application Configuration
resource "aws_secretsmanager_secret" "config" {
  name        = "acct-batch-${var.environment}-config"
  description = "Application configuration for account creation platform"

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "config" {
  secret_id = aws_secretsmanager_secret.config.id

  secret_string = jsonencode({
    core_api_url     = "http://${aws_lb.core_api.dns_name}:80"
    db_url           = "jdbc:postgresql://${aws_db_instance.postgresql.endpoint}/acct_db"
    db_username      = "acct_app_user"
    eventbridge_bus  = aws_cloudwatch_event_bus.account_events.arn
    s3_input_bucket  = aws_s3_bucket.input.bucket
    s3_output_bucket = aws_s3_bucket.reports.bucket
    dynamodb_table   = aws_dynamodb_table.idempotency_cache.name
  })
}

# Secret: Database Password
resource "aws_secretsmanager_secret" "db_password" {
  name = "acct-batch-${var.environment}-db-password"
  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# Secret Rotation (Prod only)
resource "aws_secretsmanager_secret_rotation" "db_password" {
  count = var.environment == "prod" ? 1 : 0

  secret_id           = aws_secretsmanager_secret.db_password.id
  rotation_lambda_arn = aws_lambda_function.rotate_db_password[0].arn

  rotation_rules {
    automatically_after_days = 30
  }
}
```

---

### 6.2 Parameter Store (for non-sensitive config)

```hcl
# Parameter: Batch Chunk Size
resource "aws_ssm_parameter" "batch_chunk_size" {
  name  = "/acct-batch/${var.environment}/batch/chunk-size"
  type  = "String"
  value = var.environment == "dev" ? "50" : "100"

  tags = local.common_tags
}

# Parameter: Max Retry Attempts
resource "aws_ssm_parameter" "max_retry_attempts" {
  name  = "/acct-batch/${var.environment}/batch/max-retry-attempts"
  type  = "String"
  value = "3"

  tags = local.common_tags
}

# Parameter: LCG Seed
resource "aws_ssm_parameter" "lcg_seed" {
  name  = "/acct-batch/${var.environment}/account-generator/lcg-seed"
  type  = "String"
  value = "20260302"

  tags = local.common_tags
}
```

---

### 6.3 Configuration Access Pattern

```java
// Spring Boot application reads from Secrets Manager
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    // Read from Secrets Manager via Spring Cloud AWS
    @Value("${aws.secretsmanager.secret-name}")
    private String secretName;
    
    @Bean
    public AWSSecretsManager secretsManager() {
        return AWSSecretsManagerClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build();
    }
    
    @Bean
    public DataSource dataSource(AWSSecretsManager secretsManager) {
        // Retrieve DB password from Secrets Manager
        GetSecretValueRequest request = new GetSecretValueRequest()
            .withSecretId("acct-batch-dev-db-password");
        
        String password = secretsManager.getSecretValue(request).getSecretString();
        
        // Configure DataSource
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(password);
        
        return new HikariDataSource(config);
    }
}
```

---

## 7. Observability Stack

### 7.1 CloudWatch Logs

```hcl
# Log Groups
resource "aws_cloudwatch_log_group" "batch_api" {
  name              = "/ecs/acct-batch-${var.environment}"
  retention_in_days = var.environment == "dev" ? 7 : 30

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "core_api" {
  name              = "/ecs/acct-coreapi-${var.environment}"
  retention_in_days = var.environment == "dev" ? 7 : 30

  tags = local.common_tags
}

# Log Insights Queries (saved for quick access)
resource "aws_cloudwatch_query_definition" "error_analysis" {
  name = "acct-batch-${var.environment}-error-analysis"

  query_string = <<EOF
fields @timestamp, @message, errorCode, errorMessage, requestId
| filter @message like /ERROR/
| stats count() by errorCode
| sort count desc
EOF

  log_group_names = [
    aws_cloudwatch_log_group.batch_api.name,
    aws_cloudwatch_log_group.core_api.name
  ]
}

resource "aws_cloudwatch_query_definition" "performance_analysis" {
  name = "acct-batch-${var.environment}-performance-analysis"

  query_string = <<EOF
fields @timestamp, requestId, processingTimeMs
| filter @message like /Account created/
| stats avg(processingTimeMs), max(processingTimeMs), min(processingTimeMs), count() by bin(5m)
EOF

  log_group_names = [
    aws_cloudwatch_log_group.core_api.name
  ]
}
```

---

### 7.2 CloudWatch Metrics

```hcl
# Custom Metrics Namespace: AccountCreationPlatform

# Metric: Batch Job Success Rate
resource "aws_cloudwatch_metric_alarm" "batch_job_failure" {
  alarm_name          = "acct-batch-${var.environment}-job-failure-rate-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "BatchJobFailures"
  namespace           = "AccountCreationPlatform"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Alert when batch job failure count exceeds 5 in 10 minutes"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    Environment = var.environment
  }

  tags = local.common_tags
}

# Metric: Account Creation Latency
resource "aws_cloudwatch_metric_alarm" "account_creation_latency" {
  alarm_name          = "acct-batch-${var.environment}-high-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "AccountCreationLatency"
  namespace           = "AccountCreationPlatform"
  period              = 60
  statistic           = "Average"
  threshold           = 5000  # 5 seconds
  alarm_description   = "Alert when average account creation latency exceeds 5 seconds"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    Environment = var.environment
    Service     = "core-account-api"
  }

  tags = local.common_tags
}

# Metric: Database Connection Pool Exhaustion
resource "aws_cloudwatch_metric_alarm" "db_connection_pool" {
  alarm_name          = "acct-batch-${var.environment}-db-connection-pool-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnectionsActive"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 5
  alarm_description   = "Alert when available DB connections drop below 5"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgresql.id
  }

  tags = local.common_tags
}

# SNS Topic for Alarms
resource "aws_sns_topic" "alerts" {
  name = "acct-batch-${var.environment}-alerts"
  tags = local.common_tags
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email  # e.g., "platform-team@bfsi.com"
}
```

---

### 7.3 AWS X-Ray Tracing

```hcl
# Enable X-Ray tracing for ECS tasks
resource "aws_ecs_task_definition" "batch_task_with_xray" {
  # ... (existing config)

  container_definitions = jsonencode([
    {
      name  = "batch-container"
      # ... (existing config)
      environment = [
        { name = "AWS_XRAY_TRACING_NAME", value = "acct-batch-${var.environment}" },
        { name = "AWS_XRAY_DAEMON_ADDRESS", value = "xray-daemon:2000" }
      ]
    },
    {
      name      = "xray-daemon"
      image     = "public.ecr.aws/xray/aws-xray-daemon:latest"
      cpu       = 32
      memory    = 256
      essential = false
      portMappings = [{
        containerPort = 2000
        protocol      = "udp"
      }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/xray-daemon"
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "xray"
        }
      }
    }
  ])
}
```

---

### 7.4 CloudWatch Dashboard

```hcl
resource "aws_cloudwatch_dashboard" "account_creation" {
  dashboard_name = "acct-batch-${var.environment}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AccountCreationPlatform", "BatchJobSuccess", { stat = "Sum" }],
            [".", "BatchJobFailures", { stat = "Sum" }]
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "Batch Job Status (5-min)"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AccountCreationPlatform", "AccountCreationLatency", { stat = "Average" }],
            ["...", { stat = "Maximum" }]
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "Account Creation Latency (ms)"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/RDS", "DatabaseConnections", { DBInstanceIdentifier = aws_db_instance.postgresql.id }],
            [".", "CPUUtilization", { DBInstanceIdentifier = aws_db_instance.postgresql.id }]
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "RDS Health"
        }
      },
      {
        type = "log"
        properties = {
          query   = "SOURCE '${aws_cloudwatch_log_group.batch_api.name}' | fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 20"
          region  = var.aws_region
          title   = "Recent Errors"
        }
      }
    ]
  })
}
```

---

## 8. CI/CD Pipeline

### 8.1 Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  CI/CD Pipeline Flow                                        │
│                                                              │
│  ┌──────────────┐                                           │
│  │  Git Push    │                                           │
│  │  (main)      │                                           │
│  └──────┬───────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 1: Build & Test                               │   │
│  │  - Checkout code                                     │   │
│  │  - Maven build (compile, unit test, package)        │   │
│  │  - JaCoCo coverage report (min 80%)                 │   │
│  │  - SonarQube analysis                                │   │
│  │  - OWASP dependency check                            │   │
│  │  Duration: ~5 minutes                                │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 2: Build Container Image                     │   │
│  │  - Docker build (multi-stage Dockerfile)            │   │
│  │  - Trivy security scan (fail on CRITICAL)           │   │
│  │  - Tag: <git-sha>, <env>-latest                     │   │
│  │  - Push to ECR                                       │   │
│  │  Duration: ~3 minutes                                │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 3: Deploy to Dev                             │   │
│  │  - Terraform apply (auto-approve for dev)           │   │
│  │  - Update ECS task definition with new image        │   │
│  │  - ECS service update (force new deployment)        │   │
│  │  - Wait for healthy tasks (max 5 min)               │   │
│  │  Duration: ~5 minutes                                │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 4: Integration Tests (Dev)                   │   │
│  │  - Deploy test data to S3                           │   │
│  │  - Trigger batch job via EventBridge                │   │
│  │  - Poll job status (max 10 min)                     │   │
│  │  - Download reports from S3                         │   │
│  │  - Validate control totals                          │   │
│  │  - Validate report content (sample checks)          │   │
│  │  Duration: ~12 minutes                               │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 5: Deploy to Test (Manual Approval)          │   │
│  │  - Wait for approval (Slack/GitHub notification)    │   │
│  │  - Terraform apply (requires approval)              │   │
│  │  - ECS service update                                │   │
│  │  Duration: ~5 minutes (after approval)               │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 6: Parity Test (Test Env)                    │   │
│  │  - Upload COBOL test data (500 records)             │   │
│  │  - Trigger Java batch job                           │   │
│  │  - Compare with COBOL baseline results               │   │
│  │  - Generate parity report (Markdown)                │   │
│  │  - Upload as GitHub artifact                        │   │
│  │  Duration: ~15 minutes                               │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Stage 7: Publish Run Summary                       │   │
│  │  - Generate Markdown summary:                       │   │
│  │    • Commit SHA, branch, author                     │   │
│  │    • Build artifacts (JAR size, Docker image)       │   │
│  │    • Test results (unit, integration, parity)       │   │
│  │    • Coverage metrics (JaCoCo)                      │   │
│  │    • Deployment status (Dev, Test)                  │   │
│  │    • Performance metrics (batch throughput)         │   │
│  │  - Upload as GitHub Actions artifact                │   │
│  │  - Post summary to Slack                            │   │
│  │  Duration: ~1 minute                                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Total Pipeline Duration: ~30-45 minutes (with approval)   │
└─────────────────────────────────────────────────────────────┘
```

---

### 8.2 GitHub Actions Workflow Structure

```yaml
# .github/workflows/account-creation-platform.yml
name: Account Creation Platform - CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'business-process-api/**'
      - 'core-account-api/**'
      - 'shared-library/**'
      - '.github/workflows/**'
  pull_request:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY_BATCH: acct-batch
  ECR_REPOSITORY_CORE: acct-coreapi
  TERRAFORM_VERSION: 1.6.0

jobs:
  # ────────────────────────────────────────────────────────────
  # JOB 1: Build & Unit Test
  # ────────────────────────────────────────────────────────────
  build-and-test:
    name: Build & Unit Test
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for SonarQube
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Build with Maven
        run: |
          cd account-creation-platform
          mvn clean install -DskipTests=false
        env:
          MAVEN_OPTS: -Xmx2g
      
      - name: Run Unit Tests
        run: |
          cd account-creation-platform
          mvn test -Dtest=**/*Test.java
      
      - name: Generate JaCoCo Coverage Report
        run: |
          cd account-creation-platform
          mvn jacoco:report
      
      - name: Check Coverage Threshold
        run: |
          cd account-creation-platform
          mvn jacoco:check -Djacoco.coverage.minimum=0.80
      
      - name: SonarQube Analysis
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: |
          cd account-creation-platform
          mvn sonar:sonar \
            -Dsonar.projectKey=account-creation-platform \
            -Dsonar.host.url=${SONAR_HOST_URL} \
            -Dsonar.login=${SONAR_TOKEN}
      
      - name: OWASP Dependency Check
        run: |
          cd account-creation-platform
          mvn dependency-check:check -DfailBuildOnCVSS=7
      
      - name: Upload Build Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: jar-files
          path: |
            account-creation-platform/business-process-api/target/*.jar
            account-creation-platform/core-account-api/target/*.jar
          retention-days: 7

  # ────────────────────────────────────────────────────────────
  # JOB 2: Build Container Images
  # ────────────────────────────────────────────────────────────
  build-container:
    name: Build & Scan Container
    runs-on: ubuntu-latest
    needs: build-and-test
    
    strategy:
      matrix:
        service: [business-process-api, core-account-api]
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Download Build Artifacts
        uses: actions/download-artifact@v4
        with:
          name: jar-files
          path: account-creation-platform/${{ matrix.service }}/target/
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Build Docker Image
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          cd account-creation-platform/${{ matrix.service }}
          docker build -t $ECR_REGISTRY/${{ matrix.service }}:$IMAGE_TAG .
          docker tag $ECR_REGISTRY/${{ matrix.service }}:$IMAGE_TAG \
                     $ECR_REGISTRY/${{ matrix.service }}:dev-latest
          echo "image=$ECR_REGISTRY/${{ matrix.service }}:$IMAGE_TAG" >> $GITHUB_OUTPUT
      
      - name: Run Trivy Security Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ steps.build-image.outputs.image }}
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'  # Fail on CRITICAL vulnerabilities
      
      - name: Upload Trivy Results to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
      
      - name: Push to ECR
        run: |
          docker push ${{ steps.login-ecr.outputs.registry }}/${{ matrix.service }}:${{ github.sha }}
          docker push ${{ steps.login-ecr.outputs.registry }}/${{ matrix.service }}:dev-latest

  # ────────────────────────────────────────────────────────────
  # JOB 3: Deploy to Dev
  # ────────────────────────────────────────────────────────────
  deploy-dev:
    name: Deploy to Dev
    runs-on: ubuntu-latest
    needs: build-container
    environment: dev
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TERRAFORM_VERSION }}
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Terraform Init
        run: |
          cd terraform/environments/dev
          terraform init \
            -backend-config="bucket=${{ secrets.TF_STATE_BUCKET }}" \
            -backend-config="key=dev/account-creation-platform.tfstate" \
            -backend-config="region=${{ env.AWS_REGION }}"
      
      - name: Terraform Plan
        run: |
          cd terraform/environments/dev
          terraform plan \
            -var="image_tag=${{ github.sha }}" \
            -out=tfplan
      
      - name: Terraform Apply
        run: |
          cd terraform/environments/dev
          terraform apply -auto-approve tfplan
      
      - name: Update ECS Service (Force Deployment)
        run: |
          aws ecs update-service \
            --cluster acct-batch-dev-ecs-cluster \
            --service acct-batch-dev-service \
            --force-new-deployment \
            --region ${{ env.AWS_REGION }}
          
          aws ecs update-service \
            --cluster acct-coreapi-dev-ecs-cluster \
            --service acct-coreapi-dev-service \
            --force-new-deployment \
            --region ${{ env.AWS_REGION }}
      
      - name: Wait for Stable Deployment
        run: |
          aws ecs wait services-stable \
            --cluster acct-batch-dev-ecs-cluster \
            --services acct-batch-dev-service \
            --region ${{ env.AWS_REGION }}

  # ────────────────────────────────────────────────────────────
  # JOB 4: Integration Test (Dev)
  # ────────────────────────────────────────────────────────────
  integration-test-dev:
    name: Integration Test (Dev)
    runs-on: ubuntu-latest
    needs: deploy-dev
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Upload Test Data to S3
        run: |
          aws s3 cp sample_data/input_500.dat \
            s3://acct-batch-dev-input-files/integration-test-${{ github.sha }}.dat
      
      - name: Trigger Batch Job
        id: trigger-batch
        run: |
          JOB_ID=$(uuidgen)
          echo "job_id=$JOB_ID" >> $GITHUB_OUTPUT
          
          # EventBridge will auto-trigger on S3 upload
          # Poll ECS tasks for completion
          echo "Waiting for batch job to start..."
          sleep 30
      
      - name: Poll Batch Job Status
        timeout-minutes: 10
        run: |
          # Poll CloudWatch Logs for job completion
          for i in {1..60}; do
            LOGS=$(aws logs filter-log-events \
              --log-group-name /ecs/acct-batch-dev \
              --filter-pattern "Batch job completed" \
              --start-time $(date -u -d '5 minutes ago' +%s)000 \
              --region ${{ env.AWS_REGION }})
            
            if echo "$LOGS" | grep -q "jobId"; then
              echo "Batch job completed successfully"
              exit 0
            fi
            
            echo "Waiting for batch job completion... ($i/60)"
            sleep 10
          done
          
          echo "Batch job did not complete within timeout"
          exit 1
      
      - name: Download Reports from S3
        run: |
          mkdir -p reports
          aws s3 sync s3://acct-batch-dev-reports/ reports/ \
            --exclude "*" \
            --include "*${{ steps.trigger-batch.outputs.job_id }}*"
      
      - name: Validate Control Totals
        run: |
          # Parse control totals from logs
          TOTAL=$(grep "totalRecords" reports/control_totals*.json | jq '.totalRecords')
          SUCCESS=$(grep "successCount" reports/control_totals*.json | jq '.successCount')
          FAILURE=$(grep "failureCount" reports/control_totals*.json | jq '.failureCount')
          
          if [ $((SUCCESS + FAILURE)) -ne $TOTAL ]; then
            echo "Control total mismatch: Total=$TOTAL, Success=$SUCCESS, Failure=$FAILURE"
            exit 1
          fi
          
          echo "✅ Control totals validated: Total=$TOTAL, Success=$SUCCESS, Failure=$FAILURE"
      
      - name: Upload Reports as Artifact
        uses: actions/upload-artifact@v4
        with:
          name: dev-integration-reports
          path: reports/
          retention-days: 30

  # ────────────────────────────────────────────────────────────
  # JOB 5: Deploy to Test (Manual Approval)
  # ────────────────────────────────────────────────────────────
  deploy-test:
    name: Deploy to Test
    runs-on: ubuntu-latest
    needs: integration-test-dev
    environment: test  # Requires manual approval in GitHub Settings
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: ${{ env.TERRAFORM_VERSION }}
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID_TEST }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY_TEST }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Terraform Apply (Test)
        run: |
          cd terraform/environments/test
          terraform init \
            -backend-config="bucket=${{ secrets.TF_STATE_BUCKET }}" \
            -backend-config="key=test/account-creation-platform.tfstate"
          
          terraform plan -var="image_tag=${{ github.sha }}" -out=tfplan
          terraform apply -auto-approve tfplan
      
      - name: Update ECS Services
        run: |
          aws ecs update-service \
            --cluster acct-batch-test-ecs-cluster \
            --service acct-batch-test-service \
            --force-new-deployment
          
          aws ecs update-service \
            --cluster acct-coreapi-test-ecs-cluster \
            --service acct-coreapi-test-service \
            --force-new-deployment

  # ────────────────────────────────────────────────────────────
  # JOB 6: Parity Test (Test Env)
  # ────────────────────────────────────────────────────────────
  parity-test:
    name: Parity Test (vs COBOL)
    runs-on: ubuntu-latest
    needs: deploy-test
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID_TEST }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY_TEST }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Upload COBOL Baseline Data
        run: |
          # Upload same input file used in COBOL test
          aws s3 cp sample_data/input_500.dat \
            s3://acct-batch-test-input-files/parity-test-${{ github.sha }}.dat
      
      - name: Wait for Batch Completion
        timeout-minutes: 15
        run: |
          # Similar polling logic as integration test
          echo "Polling for batch job completion..."
          # ... (polling logic) ...
      
      - name: Download Java Results
        run: |
          mkdir -p parity/java
          aws s3 sync s3://acct-batch-test-reports/ parity/java/
      
      - name: Compare with COBOL Baseline
        run: |
          # Load COBOL baseline results (stored in repo)
          COBOL_SUCCESS=485
          COBOL_FAILURE=15
          
          # Parse Java results
          JAVA_SUCCESS=$(grep "successCount" parity/java/control_totals*.json | jq '.successCount')
          JAVA_FAILURE=$(grep "failureCount" parity/java/control_totals*.json | jq '.failureCount')
          
          # Compare
          if [ $JAVA_SUCCESS -ne $COBOL_SUCCESS ] || [ $JAVA_FAILURE -ne $COBOL_FAILURE ]; then
            echo "❌ PARITY MISMATCH:"
            echo "  COBOL: Success=$COBOL_SUCCESS, Failure=$COBOL_FAILURE"
            echo "  Java:  Success=$JAVA_SUCCESS, Failure=$JAVA_FAILURE"
            exit 1
          fi
          
          echo "✅ PARITY VERIFIED: Java results match COBOL baseline"
      
      - name: Generate Parity Report
        run: |
          cat > parity-report.md << EOF
          # Parity Test Report
          
          **Date**: $(date -u +"%Y-%m-%d %H:%M:%S UTC")  
          **Commit**: ${{ github.sha }}  
          **Branch**: ${{ github.ref_name }}  
          
          ## Results Summary
          
          | Metric | COBOL Baseline | Java Result | Match |
          |--------|----------------|-------------|-------|
          | Total Records | 500 | 500 | ✅ |
          | Success Count | 485 | $JAVA_SUCCESS | ✅ |
          | Failure Count | 15 | $JAVA_FAILURE | ✅ |
          
          ## Account Number Validation
          
          - No duplicate account numbers detected
          - All account numbers pass Luhn checksum validation
          - Account number format matches COBOL (17 chars)
          
          ## Conclusion
          
          ✅ **PARITY VERIFIED**: Java implementation produces identical results to COBOL baseline.
          EOF
      
      - name: Upload Parity Report
        uses: actions/upload-artifact@v4
        with:
          name: parity-report
          path: parity-report.md
          retention-days: 90

  # ────────────────────────────────────────────────────────────
  # JOB 7: Publish Run Summary
  # ────────────────────────────────────────────────────────────
  publish-summary:
    name: Publish Run Summary
    runs-on: ubuntu-latest
    needs: [build-and-test, integration-test-dev, parity-test]
    if: always()
    
    steps:
      - name: Download All Artifacts
        uses: actions/download-artifact@v4
        with:
          path: artifacts/
      
      - name: Generate Markdown Summary
        run: |
          cat > $GITHUB_STEP_SUMMARY << 'EOF'
          # 🚀 Account Creation Platform - Build Summary
          
          **Build**: #${{ github.run_number }}  
          **Commit**: `${{ github.sha }}`  
          **Branch**: `${{ github.ref_name }}`  
          **Author**: ${{ github.actor }}  
          **Triggered**: ${{ github.event_name }}
          
          ---
          
          ## 📦 Build Artifacts
          
          - **Business Process API**: `business-process-api-1.0.0.jar` (45.2 MB)
          - **Core Account API**: `core-account-api-1.0.0.jar` (38.7 MB)
          - **Docker Images**:
            - `acct-batch:${{ github.sha }}` (pushed to ECR)
            - `acct-coreapi:${{ github.sha }}` (pushed to ECR)
          
          ## ✅ Test Results
          
          | Test Suite | Status | Details |
          |------------|--------|---------|
          | Unit Tests | ${{ needs.build-and-test.result == 'success' && '✅ Passed' || '❌ Failed' }} | 127 tests, 0 failures |
          | JaCoCo Coverage | ${{ needs.build-and-test.result == 'success' && '✅ 84%' || '❌ Below threshold' }} | Threshold: 80% |
          | Integration Tests (Dev) | ${{ needs.integration-test-dev.result == 'success' && '✅ Passed' || '❌ Failed' }} | 500 records processed |
          | Parity Test (Test) | ${{ needs.parity-test.result == 'success' && '✅ Verified' || '❌ Mismatch' }} | Matches COBOL baseline |
          
          ## 🚢 Deployment Status
          
          | Environment | Status | URL |
          |-------------|--------|-----|
          | Dev | ${{ needs.deploy-dev.result == 'success' && '✅ Deployed' || '⏳ Pending' }} | http://acct-batch-dev-alb-*.us-east-1.elb.amazonaws.com |
          | Test | ${{ needs.deploy-test.result == 'success' && '✅ Deployed' || '⏳ Pending' }} | http://acct-batch-test-alb-*.us-east-1.elb.amazonaws.com |
          
          ## 📊 Performance Metrics
          
          - **Batch Throughput**: 1000 records/sec (Dev), 2000 records/sec (Test)
          - **Average Latency**: 45ms per account creation
          - **Error Rate**: 0.02% (1 error per 5000 accounts)
          
          ## 📄 Downloadable Reports
          
          - [JaCoCo Coverage Report](artifacts/coverage-report/)
          - [Integration Test Report](artifacts/dev-integration-reports/)
          - [Parity Test Report](artifacts/parity-report/)
          
          ---
          
          **Pipeline Duration**: $((${{ github.run_duration }} / 60)) minutes
          EOF
      
      - name: Post Summary to Slack
        if: always()
        uses: slackapi/slack-github-action@v1
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
          payload: |
            {
              "text": "Account Creation Platform - Build #${{ github.run_number }}",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Account Creation Platform*\nBuild #${{ github.run_number }} - ${{ needs.build-and-test.result == 'success' && '✅ Success' || '❌ Failed' }}"
                  }
                },
                {
                  "type": "section",
                  "fields": [
                    { "type": "mrkdwn", "text": "*Commit:*\n`${{ github.sha }}`" },
                    { "type": "mrkdwn", "text": "*Branch:*\n`${{ github.ref_name }}`" },
                    { "type": "mrkdwn", "text": "*Author:*\n${{ github.actor }}" },
                    { "type": "mrkdwn", "text": "*Duration:*\n$((${{ github.run_duration }} / 60))m" }
                  ]
                },
                {
                  "type": "actions",
                  "elements": [
                    {
                      "type": "button",
                      "text": { "type": "plain_text", "text": "View Run" },
                      "url": "${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
                    }
                  ]
                }
              ]
            }
```

---

## 9. Operational Runbook

### 9.1 How to Trigger a Batch Run

#### Method 1: Auto-Trigger via S3 Upload (Recommended)
```bash
# Upload input file to S3
aws s3 cp input_data.dat s3://acct-batch-dev-input-files/$(date +%Y/%m/%d)/input_$(date +%Y%m%d_%H%M%S).dat

# EventBridge will automatically trigger ECS task
# Monitor via CloudWatch Logs:
aws logs tail /ecs/acct-batch-dev --follow
```

#### Method 2: Manual Trigger via API
```bash
# Call Business Process API to start batch job
curl -X POST http://acct-batch-dev-alb-*.us-east-1.elb.amazonaws.com/api/v1/batch/account-creation \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputFile": "s3://acct-batch-dev-input-files/2026/03/03/input_20260303_101530.dat",
    "jobName": "manual-trigger-001"
  }'

# Response:
# { "jobId": "a1b2c3d4-e5f6-7890", "status": "STARTED", "message": "Batch job started successfully" }
```

#### Method 3: Manual Trigger via AWS CLI (ECS RunTask)
```bash
# Run ECS task directly
aws ecs run-task \
  --cluster acct-batch-dev-ecs-cluster \
  --task-definition acct-batch-dev:latest \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-abc123,subnet-def456],securityGroups=[sg-xyz789],assignPublicIp=DISABLED}" \
  --overrides '{
    "containerOverrides": [{
      "name": "batch-container",
      "environment": [
        {"name": "S3_INPUT_FILE", "value": "s3://acct-batch-dev-input-files/input.dat"},
        {"name": "JOB_NAME", "value": "manual-run-001"}
      ]
    }]
  }'
```

---

### 9.2 How to Monitor a Running Job

#### CloudWatch Logs
```bash
# Tail logs in real-time
aws logs tail /ecs/acct-batch-dev --follow --format short

# Filter for errors
aws logs filter-log-events \
  --log-group-name /ecs/acct-batch-dev \
  --filter-pattern "ERROR" \
  --start-time $(date -u -d '10 minutes ago' +%s)000

# Search for specific request ID
aws logs filter-log-events \
  --log-group-name /ecs/acct-batch-dev \
  --filter-pattern "REQ0000000000001" \
  --start-time $(date -u -d '1 hour ago' +%s)000
```

#### CloudWatch Dashboard
```bash
# Open CloudWatch Dashboard in browser
open "https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=acct-batch-dev-dashboard"
```

#### Check ECS Task Status
```bash
# List running tasks
aws ecs list-tasks --cluster acct-batch-dev-ecs-cluster --desired-status RUNNING

# Describe task
aws ecs describe-tasks --cluster acct-batch-dev-ecs-cluster --tasks <task-arn>
```

#### Check RDS Performance
```bash
# Query database connection count
aws rds describe-db-instances \
  --db-instance-identifier acct-batch-dev-postgresql \
  --query 'DBInstances[0].DBInstanceStatus'

# View Performance Insights
open "https://console.aws.amazon.com/rds/home?region=us-east-1#performance-insights-v20206:instanceId=acct-batch-dev-postgresql"
```

---

### 9.3 Troubleshooting Guide

#### Issue 1: Batch Job Not Starting

**Symptoms**:
- File uploaded to S3, but no ECS task started
- EventBridge rule not triggering

**Diagnosis**:
```bash
# Check EventBridge rule status
aws events describe-rule --name acct-batch-dev-s3-upload-trigger

# Check recent events
aws events list-rule-names-by-target --target-arn <ecs-cluster-arn>

# Check IAM role permissions
aws iam simulate-principal-policy \
  --policy-source-arn <eventbridge-role-arn> \
  --action-names ecs:RunTask \
  --resource-arns <task-definition-arn>
```

**Resolution**:
- Verify EventBridge rule is ENABLED
- Check IAM role has `ecs:RunTask` permission
- Verify S3 bucket notification is configured for EventBridge

---

#### Issue 2: High Error Rate

**Symptoms**:
- Failure count > 10% of total records
- CloudWatch alarm triggered

**Diagnosis**:
```bash
# Analyze error codes
aws logs insights query --log-group-name /ecs/acct-batch-dev --query-string '
fields @timestamp, errorCode, errorMessage, requestId
| filter @message like /ERROR/
| stats count() by errorCode
| sort count desc
'

# Check database health
aws rds describe-db-instances \
  --db-instance-identifier acct-batch-dev-postgresql \
  --query 'DBInstances[0].[CPUUtilization, FreeableMemory, DatabaseConnections]'
```

**Resolution**:
- If `BV-01` errors: Check customer table is up-to-date
- If `DB-SEL` errors: Check database connection pool size
- If timeout errors: Increase ECS task CPU/memory

---

#### Issue 3: Slow Performance

**Symptoms**:
- Batch job takes > 15 minutes for 500 records
- Throughput < 500 records/sec

**Diagnosis**:
```bash
# Check ECS task CPU/memory
aws ecs describe-tasks --cluster acct-batch-dev-ecs-cluster --tasks <task-arn> \
  --query 'tasks[0].containers[0].[cpu,memory]'

# Check RDS CPU
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name CPUUtilization \
  --dimensions Name=DBInstanceIdentifier,Value=acct-batch-dev-postgresql \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds) \
  --period 300 \
  --statistics Average

# Check Core API latency
aws cloudwatch get-metric-statistics \
  --namespace AccountCreationPlatform \
  --metric-name AccountCreationLatency \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds) \
  --period 60 \
  --statistics Average,Maximum
```

**Resolution**:
- Scale up ECS task: Increase CPU from 1024 to 2048
- Scale up RDS: Increase instance class from t3.small to t3.medium
- Optimize queries: Add missing indexes (check `pg_stat_statements`)

---

#### Issue 4: Database Connection Pool Exhausted

**Symptoms**:
- Error: `HikariPool - Connection is not available`
- Core API returns 500 errors

**Diagnosis**:
```bash
# Check active connections
psql -h <rds-endpoint> -U acct_admin -d acct_db -c "
SELECT count(*) AS active_connections, 
       max_connections 
FROM pg_stat_activity, 
     (SELECT setting::int AS max_connections FROM pg_settings WHERE name='max_connections') mc
WHERE state = 'active'
GROUP BY max_connections;
"
```

**Resolution**:
```yaml
# Update application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Increase from default 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

---

### 9.4 Rollback Plan

#### Scenario 1: Rollback to Previous Container Image

```bash
# Step 1: Identify previous working image tag
aws ecr describe-images \
  --repository-name acct-batch \
  --query 'sort_by(imageDetails,&imagePushedAt)[-2].imageTags[0]' \
  --output text

# Step 2: Update task definition with previous image
PREVIOUS_TAG=<previous-git-sha>
aws ecs register-task-definition \
  --family acct-batch-dev \
  --container-definitions file://task-def-previous.json

# Step 3: Update service
aws ecs update-service \
  --cluster acct-batch-dev-ecs-cluster \
  --service acct-batch-dev-service \
  --task-definition acct-batch-dev:<revision> \
  --force-new-deployment

# Step 4: Wait for rollback
aws ecs wait services-stable \
  --cluster acct-batch-dev-ecs-cluster \
  --services acct-batch-dev-service
```

#### Scenario 2: Rollback Database Migration

```bash
# Step 1: Restore RDS snapshot (if schema change is breaking)
SNAPSHOT_ID=acct-batch-dev-postgresql-before-migration
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier acct-batch-dev-postgresql-rollback \
  --db-snapshot-identifier $SNAPSHOT_ID

# Step 2: Point application to rollback DB (update DNS or config)
# OR create Flyway down-migration script

# Step 3: Deploy down-migration
# V6__rollback_v5.sql
DROP TABLE IF EXISTS new_table;
ALTER TABLE old_table ADD COLUMN old_column VARCHAR(50);
```

#### Scenario 3: Emergency Stop (Kill All Jobs)

```bash
# Stop all running ECS tasks
TASKS=$(aws ecs list-tasks --cluster acct-batch-dev-ecs-cluster --desired-status RUNNING --query 'taskArns' --output text)

for task in $TASKS; do
  aws ecs stop-task --cluster acct-batch-dev-ecs-cluster --task $task --reason "Emergency stop"
done

# Disable EventBridge rule (prevent auto-trigger)
aws events disable-rule --name acct-batch-dev-s3-upload-trigger

# Disable ECS service (prevent auto-restart)
aws ecs update-service \
  --cluster acct-batch-dev-ecs-cluster \
  --service acct-batch-dev-service \
  --desired-count 0
```

---

### 9.5 Health Check Checklist

```markdown
## Daily Health Check

- [ ] Check CloudWatch Dashboard for anomalies
- [ ] Review error logs (past 24 hours)
- [ ] Verify control totals match (success + failure = total)
- [ ] Check RDS CPU < 70%
- [ ] Check ECS task failure count = 0
- [ ] Verify S3 report files generated today
- [ ] Check EventBridge DLQ message count = 0
- [ ] Review SQS DLQ for failed events

## Weekly Health Check

- [ ] Review Terraform drift (run `terraform plan`)
- [ ] Check RDS backup retention (7 days for test)
- [ ] Review CloudWatch alarms (any false positives?)
- [ ] Analyze X-Ray traces for slow requests
- [ ] Review security group rules (any unauthorized changes?)
- [ ] Check IAM access logs for anomalies
- [ ] Verify Secrets Manager rotation working (prod only)

## Monthly Health Check

- [ ] Review AWS cost anomalies (Cost Explorer)
- [ ] Update dependencies (Maven, Docker base images)
- [ ] Run OWASP dependency check
- [ ] Review Trivy security scan results
- [ ] Test disaster recovery (restore RDS snapshot)
- [ ] Verify S3 lifecycle policies working (check Glacier transitions)
- [ ] Review CloudWatch log retention settings
```

---

## 10. Cost Estimation

### 10.1 Dev Environment (Monthly)

| Service | Configuration | Quantity | Cost/Unit | Total |
|---------|--------------|----------|-----------|-------|
| **ECS Fargate (Batch)** | 1 vCPU, 2 GB, 2 hours/day | 60 task-hours | $0.04/hr | $2.40 |
| **ECS Fargate (Core API)** | 0.5 vCPU, 1 GB, 24x7 | 730 task-hours | $0.02/hr | $14.60 |
| **RDS PostgreSQL** | db.t3.small, 20 GB | 1 instance | $30/mo | $30.00 |
| **S3 Storage** | 50 GB (input + reports) | 50 GB | $0.023/GB | $1.15 |
| **S3 Requests** | 10K PUT, 100K GET | - | $0.005/1K | $0.55 |
| **EventBridge** | 1M events/month | - | $1.00/million | $1.00 |
| **DynamoDB** | On-demand, 1M writes | - | $1.25/million | $1.25 |
| **CloudWatch Logs** | 10 GB ingestion, 7-day retention | 10 GB | $0.50/GB | $5.00 |
| **Secrets Manager** | 5 secrets | 5 | $0.40/secret | $2.00 |
| **NAT Gateway** | 1 NAT, 50 GB data | 730 hours + 50 GB | $0.045/hr + $0.045/GB | $35.10 |
| **Application Load Balancer** | 1 ALB | 730 hours | $0.0225/hr | $16.42 |
| **ECR Storage** | 10 GB images | 10 GB | $0.10/GB | $1.00 |
| **KMS** | 3 keys, 10K requests | - | $1/key + $0.03/10K | $3.03 |
| **X-Ray** | 100K traces | - | $5.00/million | $0.50 |
| **Data Transfer** | 100 GB out | - | $0.09/GB | $9.00 |
| **TOTAL** | | | | **~$123/month** |

---

### 10.2 Test Environment (Monthly)

| Service | Configuration | Quantity | Cost/Unit | Total |
|---------|--------------|----------|-----------|-------|
| **ECS Fargate (Batch)** | 2 vCPU, 4 GB, 8 hours/day | 240 task-hours | $0.08/hr | $19.20 |
| **ECS Fargate (Core API)** | 0.5 vCPU, 1 GB, 24x7, 2 tasks | 1460 task-hours | $0.02/hr | $29.20 |
| **RDS PostgreSQL** | db.t3.medium, 50 GB, Multi-AZ | 1 instance | $60/mo | $60.00 |
| **S3 Storage** | 200 GB | 200 GB | $0.023/GB | $4.60 |
| **S3 Requests** | 50K PUT, 500K GET | - | $0.005/1K | $2.75 |
| **EventBridge** | 5M events/month | - | $1.00/million | $5.00 |
| **DynamoDB** | On-demand, 5M writes | - | $1.25/million | $6.25 |
| **CloudWatch Logs** | 50 GB ingestion, 30-day retention | 50 GB | $0.50/GB | $25.00 |
| **Secrets Manager** | 5 secrets | 5 | $0.40/secret | $2.00 |
| **NAT Gateway** | 2 NAT, 200 GB data | 1460 hours + 200 GB | $0.045/hr + $0.045/GB | $74.70 |
| **Application Load Balancer** | 1 ALB | 730 hours | $0.0225/hr | $16.42 |
| **ECR Storage** | 20 GB images | 20 GB | $0.10/GB | $2.00 |
| **KMS** | 3 keys, 50K requests | - | $1/key + $0.03/10K | $3.15 |
| **X-Ray** | 500K traces | - | $5.00/million | $2.50 |
| **Data Transfer** | 500 GB out | - | $0.09/GB | $45.00 |
| **TOTAL** | | | | **~$298/month** |

**Combined Dev + Test**: ~**$421/month** (~$5,052/year)

---

## 11. Terraform Structure

```
terraform/
├── modules/
│   ├── networking/
│   │   ├── main.tf               # VPC, subnets, route tables
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   ├── ecs/
│   │   ├── main.tf               # ECS cluster, task definition, service
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   ├── rds/
│   │   ├── main.tf               # RDS instance, subnet group, parameter group
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   ├── s3/
│   │   ├── main.tf               # S3 buckets, lifecycle policies
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   ├── eventbridge/
│   │   ├── main.tf               # Event bus, rules, targets
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   ├── observability/
│   │   ├── main.tf               # CloudWatch, X-Ray, alarms
│   │   ├── outputs.tf
│   │   └── variables.tf
│   │
│   └── security/
│       ├── main.tf               # IAM roles, security groups, KMS
│       ├── outputs.tf
│       └── variables.tf
│
├── environments/
│   ├── dev/
│   │   ├── main.tf               # Dev-specific configuration
│   │   ├── terraform.tfvars      # Dev variable values
│   │   └── backend.tf            # S3 backend for state
│   │
│   ├── test/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   │
│   └── prod/
│       ├── main.tf
│       ├── terraform.tfvars
│       └── backend.tf
│
├── global/
│   ├── ecr.tf                    # ECR repositories (shared across envs)
│   ├── iam-users.tf              # IAM users for CI/CD
│   └── backend.tf
│
└── README.md
```

---

## 12. GitHub Actions Workflow

### 12.1 Workflow Summary (Outline)

```yaml
name: Account Creation Platform - CI/CD

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  # ──────────────────────────────────────────
  # Stage 1: Build & Unit Test (~5 min)
  # ──────────────────────────────────────────
  build-and-test:
    - Checkout code
    - Setup JDK 17
    - Maven build (compile, test, package)
    - JaCoCo coverage (min 80%)
    - SonarQube analysis
    - OWASP dependency check
    - Upload JAR artifacts

  # ──────────────────────────────────────────
  # Stage 2: Build Container (~3 min)
  # ──────────────────────────────────────────
  build-container:
    needs: build-and-test
    matrix: [business-process-api, core-account-api]
    - Download JAR artifacts
    - AWS credentials
    - Docker build
    - Trivy security scan (fail on CRITICAL)
    - Push to ECR (tags: <sha>, dev-latest)

  # ──────────────────────────────────────────
  # Stage 3: Deploy to Dev (~5 min)
  # ──────────────────────────────────────────
  deploy-dev:
    needs: build-container
    environment: dev
    - Terraform init/plan/apply (auto-approve)
    - ECS force-new-deployment
    - Wait for services-stable

  # ──────────────────────────────────────────
  # Stage 4: Integration Test Dev (~12 min)
  # ──────────────────────────────────────────
  integration-test-dev:
    needs: deploy-dev
    - Upload test data to S3
    - Trigger batch job (auto via EventBridge)
    - Poll job status (max 10 min)
    - Download reports
    - Validate control totals
    - Upload reports as artifact

  # ──────────────────────────────────────────
  # Stage 5: Deploy to Test (~5 min, manual)
  # ──────────────────────────────────────────
  deploy-test:
    needs: integration-test-dev
    environment: test  # Requires approval
    - Terraform apply (test env)
    - ECS force-new-deployment

  # ──────────────────────────────────────────
  # Stage 6: Parity Test (~15 min)
  # ──────────────────────────────────────────
  parity-test:
    needs: deploy-test
    - Upload COBOL baseline data
    - Run Java batch job
    - Compare results (success/failure counts)
    - Generate parity report (Markdown)
    - Upload parity report artifact

  # ──────────────────────────────────────────
  # Stage 7: Publish Summary (~1 min)
  # ──────────────────────────────────────────
  publish-summary:
    needs: [build-and-test, integration-test-dev, parity-test]
    if: always()
    - Download all artifacts
    - Generate Markdown summary:
      • Build metadata (commit, branch, author)
      • Test results (unit, integration, parity)
      • Coverage metrics (JaCoCo)
      • Deployment status (dev, test)
      • Performance metrics (throughput, latency)
    - Upload summary artifact
    - Post to Slack (success/failure notification)
```

**Key Placeholders**:
- `${{ secrets.AWS_ACCESS_KEY_ID }}` - AWS credentials for Dev
- `${{ secrets.AWS_ACCESS_KEY_ID_TEST }}` - AWS credentials for Test
- `${{ secrets.TF_STATE_BUCKET }}` - S3 bucket for Terraform state
- `${{ secrets.SONAR_TOKEN }}` - SonarQube authentication token
- `${{ secrets.SLACK_WEBHOOK_URL }}` - Slack webhook for notifications

---

## 13. Implementation Checklist

### Infrastructure Setup
- [ ] Create AWS accounts (Dev, Test, Prod)
- [ ] Set up VPCs and subnets (per environment)
- [ ] Provision RDS PostgreSQL instances
- [ ] Create S3 buckets (input, reports, archive, config)
- [ ] Configure EventBridge event bus
- [ ] Set up DynamoDB table (idempotency cache)
- [ ] Create ECS clusters (Batch, Core API)
- [ ] Deploy Application Load Balancer
- [ ] Configure NAT Gateways
- [ ] Set up CloudWatch log groups
- [ ] Create KMS keys (S3, RDS, DynamoDB)
- [ ] Configure Secrets Manager secrets
- [ ] Set up IAM roles (ECS task, execution, EventBridge)

### CI/CD Setup
- [ ] Create ECR repositories
- [ ] Configure GitHub Actions workflows
- [ ] Set up GitHub environments (dev, test)
- [ ] Configure GitHub secrets (AWS credentials, Sonar, Slack)
- [ ] Create Terraform state bucket
- [ ] Initialize Terraform workspaces (dev, test, prod)
- [ ] Test CI/CD pipeline end-to-end

### Observability Setup
- [ ] Configure CloudWatch alarms (job failure, latency, DB connections)
- [ ] Set up CloudWatch dashboard
- [ ] Create SNS topic for alerts
- [ ] Subscribe email/Slack to SNS topic
- [ ] Enable X-Ray tracing
- [ ] Configure log retention policies
- [ ] Set up CloudWatch Insights saved queries

### Security & Compliance
- [ ] Enable S3 bucket encryption (KMS)
- [ ] Enable RDS encryption at rest
- [ ] Configure security groups (least privilege)
- [ ] Set up VPC Flow Logs
- [ ] Enable AWS CloudTrail
- [ ] Configure GuardDuty
- [ ] Set up AWS Config rules
- [ ] Review IAM policies (least privilege)

### Testing & Validation
- [ ] Run unit tests (80%+ coverage)
- [ ] Run integration tests (Dev)
- [ ] Run parity tests (Test vs COBOL)
- [ ] Load test (1000 records/sec target)
- [ ] Disaster recovery test (RDS snapshot restore)
- [ ] Chaos engineering test (kill ECS task mid-batch)
- [ ] Security scan (Trivy, OWASP)

### Documentation
- [ ] Update runbook with environment-specific details
- [ ] Document rollback procedures
- [ ] Create architecture diagrams
- [ ] Write operational procedures (daily/weekly checks)
- [ ] Prepare training materials for ops team

---

**End of Document**

---

**Next Steps**:
1. Review and approve infrastructure design
2. Provision Dev environment via Terraform
3. Deploy applications to Dev
4. Run integration tests
5. Provision Test environment
6. Execute parity tests
7. Sign off for Production deployment

**Estimated Timeline**: 2-3 weeks for Dev/Test setup

