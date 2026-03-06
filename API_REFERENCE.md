# API Reference Guide

## Business Process API (Port 8080)

### Trigger Batch Job
**Endpoint**: `POST /api/v1/batch/account-creation`

**Request**:
```json
{
  "s3InputFile": "s3://acct-input-files/input_500.dat",
  "jobName": "accountCreationJob-20260303-001"
}
```

**Response (202 Accepted)**:
```json
{
  "jobId": 12345,
  "jobName": "accountCreationJob-20260303-001",
  "status": "STARTED",
  "startTime": "2026-03-03T10:15:30.123Z",
  "s3InputFile": "s3://acct-input-files/input_500.dat"
}
```

**Error Responses**:
- `409 Conflict` - Job already running or completed
- `400 Bad Request` - Job cannot be restarted
- `500 Internal Server Error` - Failed to launch job

---

### Get Job Status
**Endpoint**: `GET /api/v1/batch/status/{jobId}`

**Response**:
```json
{
  "message": "Job status endpoint - to be implemented"
}
```

---

## Core Account API (Port 8081)

### Create Account
**Endpoint**: `POST /api/v1/core/accounts`

**Request**:
```json
{
  "requestId": "REQ0000000000001",
  "customerId": "CUST00000001",
  "customerName": "John Doe",
  "dateOfBirth": "1990-05-15",
  "accountType": "SAV",
  "currency": "INR",
  "initialDeposit": 5000.00,
  "country": "IN",
  "channel": "BRANCH"
}
```

**Response (201 Created)**:
```json
{
  "status": "CREATED",
  "requestId": "REQ0000000000001",
  "accountNumber": "12312026030312345",
  "errorCode": null,
  "errorMessage": null,
  "processingTimeMs": 125
}
```

**Error Response (500 Internal Server Error)**:
```json
{
  "status": "FAILED",
  "requestId": "REQ0000000000001",
  "accountNumber": null,
  "errorCode": "TECH-99",
  "errorMessage": "Account creation failed: Database connection timeout",
  "processingTimeMs": 30005
}
```

**Validation Errors (400 Bad Request)**:
```json
{
  "timestamp": "2026-03-03T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/core/accounts",
  "errors": [
    {
      "field": "requestId",
      "message": "Request ID is mandatory"
    },
    {
      "field": "initialDeposit",
      "message": "Must be greater than or equal to 0"
    }
  ]
}
```

---

### Health Check
**Endpoint**: `GET /api/v1/core/accounts/health`

**Response (200 OK)**:
```
Core Account API is healthy
```

---

## Error Codes

### Data Validation Errors (DV-*)
| Code | Description | HTTP Status |
|------|-------------|-------------|
| DV-01 | Mandatory field missing | 400 |
| DV-02 | Invalid account type | 400 |
| DV-03 | Invalid date of birth | 400 |
| DV-04 | Invalid currency | 400 |
| DV-05 | Invalid initial deposit | 400 |

### Business Validation Errors (BV-*)
| Code | Description | HTTP Status |
|------|-------------|-------------|
| BV-01 | Customer not found or inactive | 422 |
| BV-02 | Customer blacklisted | 422 |
| BV-03 | Channel restriction violated | 422 |
| BV-04 | Minimum deposit not met | 422 |
| BV-06 | Duplicate request ID | 422 |

### Technical Errors (TECH-*)
| Code | Description | HTTP Status | Retryable |
|------|-------------|-------------|-----------|
| TECH-01 | Service unavailable | 503 | Yes |
| TECH-02 | Internal server error | 500 | Yes |
| TECH-03 | Request timeout | 504 | Yes |
| TECH-04 | Bad request | 400 | No |
| TECH-99 | Unknown error | 500 | No |

### Generation Errors (GEN-*)
| Code | Description | HTTP Status |
|------|-------------|-------------|
| GEN-01 | Failed to generate unique account number after max retries | 500 |

---

## Input File Format (S3)

**File**: 200-byte fixed-width records

**Structure**:
```
Position  Length  Field               Example
1-20      20      REQUEST_ID          REQ0000000000001
21-32     12      CUSTOMER_ID         CUST00000001
33-52     20      CUSTOMER_NAME       John Doe
53-54     2       COUNTRY             IN
55-72     18      Filler              (spaces)
73-80     8       DATE_OF_BIRTH       19900515
81-83     3       ACCOUNT_TYPE        SAV
84-86     3       CURRENCY            INR
87-99     13      INITIAL_DEPOSIT     0000000500000 (implied 2 decimals: 5000.00)
100-109   10      CHANNEL             BRANCH
110-200   91      Filler              (spaces)
```

**Example Record**:
```
REQ0000000000001CUST00000001John Doe            IN                  199005155AVINR00000005000000BRANCH
```

---

## Output Report Format (S3)

**Files**: 
- Success: `s3://acct-reports/success_report_{jobId}_{timestamp}.dat`
- Failure: `s3://acct-reports/failure_report_{jobId}_{timestamp}.dat`

**Format**: Pipe-delimited

**Header**:
```
REQ_ID|CUST_ID|CUST_NAME|DOB|ACCT_TYPE|CURRENCY|INIT_DEP|CHANNEL|ACCT_NO|STATUS|ERR_CODE|ERR_MSG
```

**Success Record**:
```
REQ0000000000001|CUST00000001|John Doe|1990-05-15|SAV|INR|5000.00|BRANCH|12312026030312345|SUCCESS||
```

**Failure Record**:
```
REQ0000000000002|CUST00000005|Jane Smith|1985-03-20|CUR|USD|800.00|WEB||FAILED|BV-01|Customer not found or inactive
```

---

## Dead Letter Queue Message (SQS)

**Queue**: `account-creation-dlq.fifo`

**Message Structure** (JSON):
```json
{
  "requestId": "REQ0000000000003",
  "customerId": "CUST00000006",
  "errorCode": "BV-02",
  "errorMessage": "Customer is blacklisted",
  "errorDetails": "Customer with ID CUST00000006 has blacklist_flag='Y'",
  "timestamp": "2026-03-03T10:15:30.123Z",
  "originalRecord": "REQ0000000000003CUST00000006Alice Johnson     US                  198712105CUREUR00000001200000PARTNER"
}
```

**Message Attributes**:
- `messageGroupId`: "account-creation-failures"
- `messageDeduplicationId`: {requestId}

---

## EventBridge Event (AccountCreated)

**Event Bus**: `account-events`

**Event Structure**:
```json
{
  "version": "1.0",
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "eventType": "ACCOUNT_CREATED",
  "timestamp": "2026-03-03T10:15:30.123+00:00",
  "source": "core-account-api",
  "correlationId": "REQ0000000000001",
  "payload": {
    "requestId": "REQ0000000000001",
    "accountNumber": "12312026030312345",
    "customerId": "CUST00000001",
    "accountType": "SAV",
    "currency": "INR",
    "openBalance": 5000.00,
    "channel": "BRANCH",
    "status": "A",
    "openTimestamp": "2026-03-03T10:15:30.123+00:00"
  }
}
```

**Event Pattern** (for subscribers):
```json
{
  "source": ["core-account-api"],
  "detail-type": ["ACCOUNT_CREATED"]
}
```

---

## Configuration

### Business Process API (`application.yml`)
```yaml
server:
  port: 8080

core-account-api:
  base-url: http://localhost:8081

batch:
  chunk-size: 100
  skip-limit: 50
  retry-limit: 3

aws:
  s3:
    input-bucket: acct-input-files
    report-bucket: acct-reports
  sqs:
    dlq-url: https://sqs.us-east-1.amazonaws.com/123456789012/account-creation-dlq.fifo

resilience4j:
  circuitbreaker:
    instances:
      coreAccountApi:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      coreAccountApi:
        max-attempts: 3
        wait-duration: 2s
```

### Core Account API (`application.yml`)
```yaml
server:
  port: 8081

aws:
  dynamodb:
    table-name: idempotency-cache
    endpoint: http://localhost:4566  # LocalStack
  eventbridge:
    bus-name: account-events
    endpoint: http://localhost:4566  # LocalStack

account:
  generation:
    lcg-seed: 12345
    max-collision-retries: 10
```

---

## Testing with cURL

### Trigger Batch Job
```bash
curl -X POST http://localhost:8080/api/v1/batch/account-creation \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputFile": "s3://acct-input-files/input_500.dat",
    "jobName": "test-job-001"
  }'
```

### Create Single Account
```bash
curl -X POST http://localhost:8081/api/v1/core/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "requestId": "REQ0000000000001",
    "customerId": "CUST00000001",
    "customerName": "John Doe",
    "dateOfBirth": "1990-05-15",
    "accountType": "SAV",
    "currency": "INR",
    "initialDeposit": 5000.00,
    "country": "IN",
    "channel": "BRANCH"
  }'
```

### Health Check
```bash
curl http://localhost:8081/api/v1/core/accounts/health
```

---

Generated by GitHub Copilot
Date: March 3, 2026
