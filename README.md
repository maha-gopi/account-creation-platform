# Account Creation Platform

Cloud-native account creation system for BFSI, modernized from mainframe COBOL batch job.

## Architecture

This project implements **Option B: Event-Driven Architecture** with two microservices:

1. **Business Process API**: Orchestration, validations (DV-*, BV-*), Spring Batch processing
2. **Core Account API**: Persistence layer, account number generation, event publishing

## Project Structure

```
account-creation-platform/
├── shared-library/          # Common DTOs, enums, exceptions
├── core-account-api/        # Core persistence microservice
├── business-process-api/    # Business logic microservice
├── infrastructure/          # Terraform IaC
├── docs/                    # API specs, runbooks
└── scripts/                 # Utility scripts
```

## Technology Stack

- **Java 17** (LTS)
- **Spring Boot 3.2.x**
- **Spring Batch 5.1.x** (chunk-oriented processing)
- **PostgreSQL 15.x** (Amazon RDS)
- **AWS Services**: S3, SQS, EventBridge, DynamoDB, ECS Fargate
- **Build Tool**: Maven 3.9.x

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker (for local development)
- PostgreSQL 15+ (or use Docker Compose)
- AWS CLI (for deployment)

## Quick Start

### 1. Build All Modules

```bash
cd account-creation-platform
mvn clean install
```

### 2. Start Local Environment (Docker Compose)

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- LocalStack (AWS services emulation)

### 3. Run Core Account API

```bash
cd core-account-api
mvn spring-boot:run
```

API runs on: http://localhost:8081

### 4. Run Business Process API

```bash
cd business-process-api
mvn spring-boot:run
```

API runs on: http://localhost:8080

### 5. Trigger Batch Job

```bash
curl -X POST http://localhost:8080/api/v1/accounts/batch-process \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputFile": "s3://acct-input-files/input_500.dat",
    "jobName": "accountCreationJob"
  }'
```

## API Documentation

- **Business Process API**: http://localhost:8080/swagger-ui.html
- **Core Account API**: http://localhost:8081/swagger-ui.html

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests (Testcontainers)

```bash
mvn verify
```

## Configuration

### Environment Variables

**Business Process API:**
- `DB_HOST`: PostgreSQL host (default: localhost)
- `DB_PORT`: PostgreSQL port (default: 5432)
- `DB_NAME`: Database name (default: acct_db)
- `DB_USERNAME`: Database user (default: acct_user)
- `DB_PASSWORD`: Database password
- `CORE_API_URL`: Core API base URL (default: http://localhost:8081)
- `S3_INPUT_BUCKET`: S3 bucket for input files
- `S3_REPORT_BUCKET`: S3 bucket for reports
- `AWS_REGION`: AWS region (default: us-east-1)

**Core Account API:**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`: Same as above
- `DYNAMODB_TABLE`: DynamoDB table for idempotency cache
- `EVENTBRIDGE_BUS`: EventBridge custom bus name
- `AWS_REGION`: AWS region

## Business Rules Implemented

### Data Validations (DV-*)
- **DV-01**: Mandatory fields (REQUEST_ID, CUSTOMER_ID)
- **DV-02**: Valid ACCOUNT_TYPE (SAV, CUR, LOA)
- **DV-03**: Date of birth range (1900-2099)
- **DV-04**: Valid CURRENCY (INR, USD, EUR)
- **DV-05**: Initial deposit >= 0

### Business Validations (BV-*)
- **BV-01**: Customer exists and is active
- **BV-02**: Customer not blacklisted
- **BV-03**: Channel restrictions (PARTNER cannot open LOA)
- **BV-04**: Minimum opening balance (SAV: 500, CUR: 1000)
- **BV-06**: Duplicate REQUEST_ID check (idempotency)

### Technical Operations
- **GEN-01**: Account number generation (LCG + Luhn checksum)
- **GEN-02**: Collision detection and retry (max 10 attempts)
- **DB-INS**: Atomic insert (account + audit)
- **EVT-01**: Event publishing to EventBridge

## Idempotency

The system guarantees idempotent account creation using:
1. **PostgreSQL unique constraint** on `request_id`
2. **DynamoDB cache** (7-day TTL) for fast duplicate detection
3. **Spring Batch restart** capability from last committed chunk

## Deployment

### AWS ECS Fargate Deployment

```bash
cd infrastructure/terraform/environments/prod
terraform init
terraform plan
terraform apply
```

### CI/CD Pipeline

GitHub Actions workflows are defined in `.github/workflows/`:
- `build.yml`: Build and test on every commit
- `deploy-dev.yml`: Auto-deploy to dev on merge to main
- `deploy-prod.yml`: Manual deploy to prod

## Monitoring & Observability

- **CloudWatch Metrics**: Custom namespace `AccountCreation`
- **AWS X-Ray**: Distributed tracing
- **Health Checks**: `/actuator/health` on both services
- **Logs**: Structured JSON logs to CloudWatch

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

Proprietary - Internal BFSI Project

## Contact

Modernization Engineering Team
