# 06 - Test Scenarios & Data

**Document Version**: 1.0  
**Date**: March 3, 2026  
**Test Environment**: Dev/Test  
**Status**: Ready for Execution

---

## Table of Contents

1. [Test Strategy Overview](#1-test-strategy-overview)
2. [Test Data Management](#2-test-data-management)
3. [Unit Test Scenarios](#3-unit-test-scenarios)
4. [Integration Test Scenarios](#4-integration-test-scenarios)
5. [Parity Test Scenarios](#5-parity-test-scenarios)
6. [Performance Test Scenarios](#6-performance-test-scenarios)
7. [Negative Test Scenarios](#7-negative-test-scenarios)
8. [Edge Case Test Scenarios](#8-edge-case-test-scenarios)
9. [Test Data Files](#9-test-data-files)
10. [Test Execution Checklist](#10-test-execution-checklist)

---

## 1. Test Strategy Overview

### 1.1 Testing Pyramid

```
                  ┌─────────────────┐
                  │  E2E Parity     │  5% (20 scenarios)
                  │  Tests          │
                  └─────────────────┘
                ┌─────────────────────┐
                │  Integration Tests  │  15% (60 scenarios)
                │  (API + Batch)      │
                └─────────────────────┘
              ┌───────────────────────────┐
              │  Unit Tests               │  80% (320+ scenarios)
              │  (Service, Validator,     │
              │   Generator)              │
              └───────────────────────────┘
```

### 1.2 Test Phases

| Phase | Purpose | Environment | Duration | Pass Criteria |
|-------|---------|-------------|----------|---------------|
| **Phase 1: Unit Testing** | Validate individual components | Local (Maven) | 2 minutes | 100% pass, 80%+ coverage |
| **Phase 2: Integration Testing** | Validate API interactions | Dev | 15 minutes | 100% pass, all APIs respond |
| **Phase 3: Parity Testing** | Compare with COBOL baseline | Test | 30 minutes | 95%+ match with COBOL |
| **Phase 4: Performance Testing** | Validate throughput targets | Test | 1 hour | 1000+ records/sec |
| **Phase 5: UAT** | Business validation | Test | 2 weeks | Sign-off from business |

### 1.3 Test Coverage Goals

| Component | Target Coverage | Actual Coverage | Status |
|-----------|-----------------|-----------------|--------|
| **Validators** | 100% | TBD | ⏳ |
| **Services** | 90% | TBD | ⏳ |
| **Repositories** | 80% | TBD | ⏳ |
| **Controllers** | 85% | TBD | ⏳ |
| **Generators** | 100% | TBD | ⏳ |
| **Overall** | 80% | TBD | ⏳ |

---

## 2. Test Data Management

### 2.1 Test Data Sources

```
test-data/
├── input/
│   ├── valid/
│   │   ├── input_10_all_valid.dat           # 10 perfect records
│   │   ├── input_100_mixed.dat              # 80 valid, 20 invalid
│   │   ├── input_500_baseline.dat           # COBOL parity baseline
│   │   ├── input_1000_performance.dat       # Performance test
│   │   └── input_10000_stress.dat           # Stress test
│   │
│   ├── invalid/
│   │   ├── input_dv01_missing_fields.dat    # DV-01 violations
│   │   ├── input_dv03_invalid_dob.dat       # DV-03 violations
│   │   ├── input_bv01_customer_not_found.dat # BV-01 violations
│   │   └── input_malformed.dat              # Corrupt file
│   │
│   └── edge_cases/
│       ├── input_unicode_names.dat          # Special characters
│       ├── input_boundary_values.dat        # Min/max values
│       └── input_duplicate_requests.dat     # Idempotency test
│
├── reference/
│   ├── customer_master.sql                  # Customer reference data
│   ├── blacklist.sql                        # Blacklisted customers
│   └── existing_accounts.sql                # Pre-existing accounts
│
└── expected/
    ├── cobol_baseline/
    │   ├── success_report_cobol_500.dat     # COBOL success output
    │   ├── failure_report_cobol_500.dat     # COBOL failure output
    │   └── control_totals_cobol_500.json    # COBOL control totals
    │
    └── java_expected/
        ├── success_report_java_500.dat      # Expected Java output
        ├── failure_report_java_500.dat      # Expected Java output
        └── control_totals_java_500.json     # Expected Java totals
```

### 2.2 Test Customer Data

```sql
-- Insert test customers into customer table
INSERT INTO acct_owner.customer (customer_id, customer_name, date_of_birth, country, status, blacklist_flag) VALUES
-- Valid active customers
('CUST00000001', 'Alice Johnson',     '1990-05-15', 'US', 'A', 'N'),
('CUST00000002', 'Bob Smith',         '1985-08-22', 'IN', 'A', 'N'),
('CUST00000003', 'Charlie Brown',     '1992-11-30', 'GB', 'A', 'N'),
('CUST00000004', 'Diana Prince',      '1988-03-10', 'US', 'A', 'N'),
('CUST00000005', 'Eve Adams',         '1995-07-01', 'CA', 'A', 'N'),

-- Inactive customers (BV-01 violations)
('CUST00000010', 'Frank Miller',      '1980-12-25', 'US', 'I', 'N'),
('CUST00000011', 'Grace Hopper',      '1975-04-18', 'IN', 'I', 'N'),

-- Blacklisted customers (BV-02 violations)
('CUST00000020', 'Harry Potter',      '1990-07-31', 'GB', 'A', 'Y'),
('CUST00000021', 'Ivy League',        '1985-09-15', 'US', 'A', 'Y'),

-- Customers with existing accounts (BV-06 violations - via request_id)
('CUST00000030', 'Jack Sparrow',      '1982-06-20', 'US', 'A', 'N'),
('CUST00000031', 'Karen Walker',      '1993-02-14', 'CA', 'A', 'N');
```

### 2.3 Input File Format (200-byte Fixed-Width)

```
Position  Length  Field Name         Sample Value           Notes
--------  ------  -----------------  ---------------------  ---------------------------
1-20      20      REQUEST_ID         REQ0000000000001       Unique, left-padded spaces
21-32     12      CUSTOMER_ID        CUST00000001           References customer table
33-52     20      CUSTOMER_NAME      Alice Johnson          Spaces trimmed
53-54     2       COUNTRY            US                     2-char ISO code
55-72     18      ADDRESS            123 Main St            Optional
73-80     8       DOB                19900515               YYYYMMDD format
81-83     3       ACCOUNT_TYPE       SAV                    SAV, CUR, LOA
84-86     3       CURRENCY           INR                    INR, USD, EUR
87-99     13      INITIAL_DEPOSIT    000000500000           Implied 2 decimals (5000.00)
100-109   10      CHANNEL            BRANCH                 BRANCH, ATM, ONLINE, PARTNER
110-200   91      FILLER             (spaces)               Reserved for future use
```

**Sample Record**:
```
REQ0000000000001 CUST00000001Alice Johnson       US123 Main St        19900515SAV INR000000500000BRANCH    [81 spaces]
```

---

## 3. Unit Test Scenarios

### 3.1 Data Validation Service Tests

#### DV-01: Mandatory Fields

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-DV01-001** | Missing REQUEST_ID | `requestId=null` | `DataValidationException("DV-01")` | ⏳ |
| **UT-DV01-002** | Empty REQUEST_ID | `requestId=""` | `DataValidationException("DV-01")` | ⏳ |
| **UT-DV01-003** | Whitespace REQUEST_ID | `requestId="   "` | `DataValidationException("DV-01")` | ⏳ |
| **UT-DV01-004** | Missing CUSTOMER_ID | `customerId=null` | `DataValidationException("DV-01")` | ⏳ |
| **UT-DV01-005** | Empty CUSTOMER_ID | `customerId=""` | `DataValidationException("DV-01")` | ⏳ |
| **UT-DV01-006** | Valid mandatory fields | All fields present | No exception | ⏳ |

#### DV-02: Account Type Validation

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-DV02-001** | Valid SAV type | `accountType="SAV"` | No exception | ⏳ |
| **UT-DV02-002** | Valid CUR type | `accountType="CUR"` | No exception | ⏳ |
| **UT-DV02-003** | Valid LOA type | `accountType="LOA"` | No exception | ⏳ |
| **UT-DV02-004** | Invalid type XXX | `accountType="XXX"` | `DataValidationException("DV-02")` | ⏳ |
| **UT-DV02-005** | Null account type | `accountType=null` | `DataValidationException("DV-02")` | ⏳ |
| **UT-DV02-006** | Lowercase type "sav" | `accountType="sav"` | No exception (converted to SAV) | ⏳ |

#### DV-03: Date of Birth Validation

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-DV03-001** | Valid DOB (1990) | `dob="1990-05-15"` | No exception | ⏳ |
| **UT-DV03-002** | DOB year < 1900 | `dob="1899-12-31"` | `DataValidationException("DV-03")` | ⏳ |
| **UT-DV03-003** | DOB year > 2099 | `dob="2100-01-01"` | `DataValidationException("DV-03")` | ⏳ |
| **UT-DV03-004** | Future DOB | `dob="2030-01-01"` | `DataValidationException("DV-03")` | ⏳ |
| **UT-DV03-005** | Boundary: 1900-01-01 | `dob="1900-01-01"` | No exception | ⏳ |
| **UT-DV03-006** | Boundary: 2099-12-31 | `dob="2099-12-31"` | No exception (but > today) | ⏳ |
| **UT-DV03-007** | Invalid date format | `dob="31-12-1990"` | `DateTimeParseException` | ⏳ |
| **UT-DV03-008** | Null DOB | `dob=null` | `DataValidationException("DV-03")` | ⏳ |

#### DV-04: Currency Validation

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-DV04-001** | Valid INR | `currency="INR"` | No exception | ⏳ |
| **UT-DV04-002** | Valid USD | `currency="USD"` | No exception | ⏳ |
| **UT-DV04-003** | Valid EUR | `currency="EUR"` | No exception | ⏳ |
| **UT-DV04-004** | Invalid GBP | `currency="GBP"` | `DataValidationException("DV-04")` | ⏳ |
| **UT-DV04-005** | Null currency | `currency=null` | `DataValidationException("DV-04")` | ⏳ |
| **UT-DV04-006** | Lowercase "inr" | `currency="inr"` | No exception (converted to INR) | ⏳ |

#### DV-05: Initial Deposit Validation

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-DV05-001** | Valid deposit 5000.00 | `deposit=5000.00` | No exception | ⏳ |
| **UT-DV05-002** | Zero deposit | `deposit=0.00` | No exception | ⏳ |
| **UT-DV05-003** | Negative deposit | `deposit=-100.00` | `DataValidationException("DV-05")` | ⏳ |
| **UT-DV05-004** | Scale > 2 (3 decimals) | `deposit=5000.123` | `DataValidationException("DV-05")` | ⏳ |
| **UT-DV05-005** | Scale = 1 (1 decimal) | `deposit=5000.1` | No exception (scaled to 5000.10) | ⏳ |
| **UT-DV05-006** | Large value 9999999999.99 | `deposit=9999999999.99` | No exception | ⏳ |
| **UT-DV05-007** | Null deposit | `deposit=null` | `DataValidationException("DV-05")` | ⏳ |

**Unit Test Coverage**: 30+ scenarios for data validation

---

### 3.2 Business Validation Service Tests

#### BV-01: Customer Exists and Active

| Test ID | Scenario | Setup | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-BV01-001** | Customer exists, active | Customer in DB, status='A' | No exception | ⏳ |
| **UT-BV01-002** | Customer not found | Customer not in DB | `BusinessValidationException("BV-01")` | ⏳ |
| **UT-BV01-003** | Customer inactive | Customer in DB, status='I' | `BusinessValidationException("BV-01")` | ⏳ |
| **UT-BV01-004** | Customer suspended | Customer in DB, status='S' | `BusinessValidationException("BV-01")` | ⏳ |

#### BV-02: Not Blacklisted

| Test ID | Scenario | Setup | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-BV02-001** | Customer not blacklisted | blacklist_flag='N' | No exception | ⏳ |
| **UT-BV02-002** | Customer blacklisted | blacklist_flag='Y' | `BusinessValidationException("BV-02")` | ⏳ |

#### BV-03: Channel Restriction

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-BV03-001** | PARTNER + SAV | channel=PARTNER, type=SAV | No exception | ⏳ |
| **UT-BV03-002** | PARTNER + CUR | channel=PARTNER, type=CUR | No exception | ⏳ |
| **UT-BV03-003** | PARTNER + LOA | channel=PARTNER, type=LOA | `BusinessValidationException("BV-03")` | ⏳ |
| **UT-BV03-004** | BRANCH + LOA | channel=BRANCH, type=LOA | No exception | ⏳ |

#### BV-04: Minimum Deposit

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-BV04-001** | SAV with 500 | type=SAV, deposit=500 | No exception | ⏳ |
| **UT-BV04-002** | SAV with 499 | type=SAV, deposit=499 | `BusinessValidationException("BV-04")` | ⏳ |
| **UT-BV04-003** | CUR with 1000 | type=CUR, deposit=1000 | No exception | ⏳ |
| **UT-BV04-004** | CUR with 999 | type=CUR, deposit=999 | `BusinessValidationException("BV-04")` | ⏳ |
| **UT-BV04-005** | LOA with 0 | type=LOA, deposit=0 | No exception (no min for LOA) | ⏳ |

#### BV-06: Duplicate Request (Idempotency)

| Test ID | Scenario | Setup | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-BV06-001** | New request ID | Request not in DB | No exception | ⏳ |
| **UT-BV06-002** | Duplicate request ID | Request already in DB | `BusinessValidationException("BV-06")` | ⏳ |
| **UT-BV06-003** | Cached request ID | Request in DynamoDB cache | Return cached account number | ⏳ |

**Unit Test Coverage**: 20+ scenarios for business validation

---

### 3.3 Account Number Generator Tests

#### LCG Algorithm

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-GEN-001** | Generate with seed 20260302 | seed=20260302 | Deterministic sequence | ⏳ |
| **UT-GEN-002** | Generate 1000 numbers | iterations=1000 | All unique | ⏳ |
| **UT-GEN-003** | LCG constants match COBOL | a=1103515245, c=12345 | Same output as COBOL | ⏳ |
| **UT-GEN-004** | Seed overflow handling | seed=2147483647 | No exception, valid number | ⏳ |

#### Luhn Checksum

| Test ID | Scenario | Input | Expected Output | Status |
|---------|----------|-------|-----------------|--------|
| **UT-LUHN-001** | Validate checksum "1234567890123456" | 16-digit number | Checksum = 5 | ⏳ |
| **UT-LUHN-002** | Validate known valid number | "79927398713" | `isValid() = true` | ⏳ |
| **UT-LUHN-003** | Validate known invalid number | "79927398714" | `isValid() = false` | ⏳ |
| **UT-LUHN-004** | All zeros | "0000000000000000" | Checksum = 0 | ⏳ |
| **UT-LUHN-005** | All nines | "9999999999999999" | Checksum = 3 | ⏳ |

#### Collision Handling

| Test ID | Scenario | Setup | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **UT-COL-001** | No collision | Account number not in DB | Return account number | ⏳ |
| **UT-COL-002** | 1 collision, then unique | 1st number exists, 2nd unique | Return 2nd number | ⏳ |
| **UT-COL-003** | Max retries (10) exceeded | All 10 numbers exist | `TechnicalException("GEN-01")` | ⏳ |
| **UT-COL-004** | Retry count increments | Mock collisions | Verify retry logic | ⏳ |

**Unit Test Coverage**: 15+ scenarios for account generation

---

### 3.4 Repository Tests

| Test ID | Scenario | Operation | Expected Result | Status |
|---------|----------|-----------|-----------------|--------|
| **UT-REPO-001** | Save valid account | `save(account)` | Returns saved entity with ID | ⏳ |
| **UT-REPO-002** | Find by ID | `findById(accountNo)` | Returns Optional with account | ⏳ |
| **UT-REPO-003** | Find by non-existent ID | `findById("INVALID")` | Returns Optional.empty() | ⏳ |
| **UT-REPO-004** | Check exists by request ID | `existsByRequestId(requestId)` | Returns true/false | ⏳ |
| **UT-REPO-005** | Duplicate key violation | Save with duplicate account_no | `DataIntegrityViolationException` | ⏳ |
| **UT-REPO-006** | Save audit record | `auditRepo.save(audit)` | Returns saved audit with ID | ⏳ |

**Unit Test Coverage**: 10+ scenarios for repositories

---

## 4. Integration Test Scenarios

### 4.1 Core Account API Tests

#### Endpoint: POST /api/v1/accounts

| Test ID | Scenario | Request Body | Expected Response | Status Code | Status |
|---------|----------|--------------|-------------------|-------------|--------|
| **IT-API-001** | Create SAV account | Valid SAV request | `{ accountNumber: "123...", status: "SUCCESS" }` | 201 | ⏳ |
| **IT-API-002** | Create CUR account | Valid CUR request | `{ accountNumber: "123...", status: "SUCCESS" }` | 201 | ⏳ |
| **IT-API-003** | Create LOA account | Valid LOA request | `{ accountNumber: "123...", status: "SUCCESS" }` | 201 | ⏳ |
| **IT-API-004** | DV-01 violation | Missing customer ID | `{ errorCode: "DV-01", message: "..." }` | 400 | ⏳ |
| **IT-API-005** | BV-01 violation | Customer not found | `{ errorCode: "BV-01", message: "..." }` | 422 | ⏳ |
| **IT-API-006** | BV-02 violation | Blacklisted customer | `{ errorCode: "BV-02", message: "..." }` | 422 | ⏳ |
| **IT-API-007** | BV-06 violation | Duplicate request ID | `{ errorCode: "BV-06", accountNumber: "..." }` | 200 (idempotent) | ⏳ |
| **IT-API-008** | DB connection failure | Simulate DB down | `{ errorCode: "DB-ERR", message: "..." }` | 500 | ⏳ |

**Sample Request**:
```json
POST /api/v1/accounts
Content-Type: application/json

{
  "requestId": "REQ0000000000001",
  "customerId": "CUST00000001",
  "customerName": "Alice Johnson",
  "country": "US",
  "dateOfBirth": "1990-05-15",
  "accountType": "SAV",
  "currency": "INR",
  "initialDeposit": 5000.00,
  "channel": "BRANCH"
}
```

**Sample Response (Success)**:
```json
HTTP 201 Created

{
  "accountNumber": "12312026030312345",
  "requestId": "REQ0000000000001",
  "customerId": "CUST00000001",
  "status": "SUCCESS",
  "message": "Account created successfully",
  "timestamp": "2026-03-03T10:15:30.123Z"
}
```

**Sample Response (Validation Error)**:
```json
HTTP 400 Bad Request

{
  "errorCode": "DV-01",
  "errorMessage": "Customer ID is mandatory",
  "requestId": "REQ0000000000001",
  "timestamp": "2026-03-03T10:15:30.123Z"
}
```

---

### 4.2 Batch Job Tests

#### Endpoint: POST /api/v1/batch/account-creation

| Test ID | Scenario | Input File | Expected Result | Status |
|---------|----------|------------|-----------------|--------|
| **IT-BATCH-001** | Process 10 valid records | input_10_all_valid.dat | 10 success, 0 failures | ⏳ |
| **IT-BATCH-002** | Process 100 mixed records | input_100_mixed.dat | 80 success, 20 failures | ⏳ |
| **IT-BATCH-003** | Process 500 baseline | input_500_baseline.dat | 485 success, 15 failures | ⏳ |
| **IT-BATCH-004** | Empty input file | input_empty.dat | 0 success, 0 failures, warning | ⏳ |
| **IT-BATCH-005** | Malformed input file | input_malformed.dat | 0 success, all failures | ⏳ |
| **IT-BATCH-006** | S3 file not found | s3://invalid/path.dat | HTTP 400, file not found | ⏳ |
| **IT-BATCH-007** | Concurrent batch jobs | 3 jobs in parallel | All complete without conflict | ⏳ |
| **IT-BATCH-008** | Mid-batch failure | Simulate DB failure at record 50 | Rollback chunk, retry | ⏳ |

**Sample Request**:
```json
POST /api/v1/batch/account-creation
Content-Type: application/json

{
  "s3InputFile": "s3://acct-batch-dev-input-files/2026/03/03/input_500_baseline.dat",
  "jobName": "integration-test-001"
}
```

**Sample Response**:
```json
HTTP 202 Accepted

{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "STARTED",
  "message": "Batch job started successfully",
  "s3InputFile": "s3://acct-batch-dev-input-files/2026/03/03/input_500_baseline.dat",
  "startTime": "2026-03-03T10:15:30.123Z"
}
```

---

### 4.3 EventBridge Integration Tests

| Test ID | Scenario | Trigger | Expected Event | Status |
|---------|----------|---------|----------------|--------|
| **IT-EVENT-001** | Account created event | Create account via API | Event published to EventBridge | ⏳ |
| **IT-EVENT-002** | Event routing to SQS | Account created | Message in SQS queue | ⏳ |
| **IT-EVENT-003** | Event archive | Account created | Event in archive (retrievable) | ⏳ |
| **IT-EVENT-004** | EventBridge failure | Simulate EventBridge down | Event in DLQ | ⏳ |
| **IT-EVENT-005** | Event deduplication | Create account twice (same requestId) | Only 1 event published | ⏳ |

**Sample Event**:
```json
{
  "version": "1.0",
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "eventType": "ACCOUNT_CREATED",
  "timestamp": "2026-03-03T10:15:30.123Z",
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
    "openTimestamp": "2026-03-03T10:15:30.123Z"
  }
}
```

---

### 4.4 Database Integration Tests

| Test ID | Scenario | Operation | Expected Result | Status |
|---------|----------|-----------|-----------------|--------|
| **IT-DB-001** | Insert account | INSERT INTO account | 1 row affected | ⏳ |
| **IT-DB-002** | Insert audit | INSERT INTO account_audit | 1 row affected | ⏳ |
| **IT-DB-003** | Duplicate account number | INSERT with existing account_no | Unique constraint violation | ⏳ |
| **IT-DB-004** | Duplicate request ID | INSERT with existing request_id | Unique constraint violation | ⏳ |
| **IT-DB-005** | Foreign key constraint | INSERT with invalid customer_id | Foreign key violation | ⏳ |
| **IT-DB-006** | Transaction rollback | Insert account + audit, force error | Both rolled back | ⏳ |
| **IT-DB-007** | Connection pool exhaustion | 100 concurrent requests | All handled, no timeout | ⏳ |

---

### 4.5 S3 Integration Tests

| Test ID | Scenario | Operation | Expected Result | Status |
|---------|----------|-----------|-----------------|--------|
| **IT-S3-001** | Read input file | Read from S3 input bucket | File content returned | ⏳ |
| **IT-S3-002** | Write success report | Write to S3 reports bucket | File uploaded successfully | ⏳ |
| **IT-S3-003** | Write failure report | Write to S3 reports bucket | File uploaded successfully | ⏳ |
| **IT-S3-004** | EventBridge trigger on upload | Upload file to input bucket | Batch job auto-triggered | ⏳ |
| **IT-S3-005** | S3 bucket not accessible | Simulate S3 down | HTTP 500, error logged | ⏳ |

---

## 5. Parity Test Scenarios

### 5.1 Control Totals Parity

| Test ID | Scenario | COBOL Baseline | Java Expected | Pass Criteria | Status |
|---------|----------|----------------|---------------|---------------|--------|
| **PT-TOTAL-001** | Total records | 500 | 500 | Exact match | ⏳ |
| **PT-TOTAL-002** | Success count | 485 | 485 | Exact match | ⏳ |
| **PT-TOTAL-003** | Failure count | 15 | 15 | Exact match | ⏳ |
| **PT-TOTAL-004** | Control formula | Success + Failure = Total | Success + Failure = Total | Formula holds | ⏳ |

### 5.2 Validation Parity

| Test ID | Validation Rule | COBOL Result | Java Result | Pass Criteria | Status |
|---------|-----------------|--------------|-------------|---------------|--------|
| **PT-DV01-001** | Missing REQUEST_ID | FAIL (DV-01) | FAIL (DV-01) | Error code matches | ⏳ |
| **PT-DV01-002** | Missing CUSTOMER_ID | FAIL (DV-01) | FAIL (DV-01) | Error code matches | ⏳ |
| **PT-DV03-001** | DOB year < 1900 | FAIL (DV-03) | FAIL (DV-03) | Error code matches | ⏳ |
| **PT-DV03-002** | DOB year > 2099 | FAIL (DV-03) | FAIL (DV-03) | Error code matches | ⏳ |
| **PT-DV03-003** | Future DOB | PASS | FAIL (DV-03) | ⚠️ Known difference (Java stricter) | ⏳ |
| **PT-DV04-001** | Invalid currency GBP | FAIL (DV-04) | FAIL (DV-04) | Error code matches | ⏳ |
| **PT-DV05-001** | Negative deposit | FAIL (DV-05) | FAIL (DV-05) | Error code matches | ⏳ |
| **PT-BV01-001** | Customer not found | FAIL (BV-01) | FAIL (BV-01) | Error code matches | ⏳ |
| **PT-BV01-002** | Customer inactive | FAIL (BV-01) | FAIL (BV-01) | Error code matches | ⏳ |
| **PT-BV02-001** | Customer blacklisted | FAIL (BV-02) | FAIL (BV-02) | Error code matches | ⏳ |
| **PT-BV03-001** | PARTNER + LOA | FAIL (BV-03) | FAIL (BV-03) | Error code matches | ⏳ |
| **PT-BV04-001** | SAV deposit < 500 | FAIL (BV-04) | FAIL (BV-04) | Error code matches | ⏳ |
| **PT-BV04-002** | CUR deposit < 1000 | FAIL (BV-04) | FAIL (BV-04) | Error code matches | ⏳ |
| **PT-BV06-001** | Duplicate request ID | FAIL (BV-06) | FAIL (BV-06) | Error code matches | ⏳ |

### 5.3 Account Generation Parity

| Test ID | Scenario | COBOL Baseline | Java Expected | Pass Criteria | Status |
|---------|----------|----------------|---------------|---------------|--------|
| **PT-GEN-001** | Account number format | 17 chars | 17 chars | ⚠️ After remediation | ⏳ |
| **PT-GEN-002** | Account type code | 2 digits (01, 02, 03) | 2 digits (01, 02, 03) | ⚠️ After remediation | ⏳ |
| **PT-GEN-003** | Luhn checksum | All valid | All valid | 100% pass Luhn validation | ⏳ |
| **PT-GEN-004** | No duplicate accounts | 485 unique | 485 unique | No duplicates | ⏳ |
| **PT-GEN-005** | Collision retry | Max 10 retries | Max 10 retries | Same behavior | ⏳ |

### 5.4 Report Parity

| Test ID | Scenario | COBOL Format | Java Format | Pass Criteria | Status |
|---------|----------|--------------|-------------|---------------|--------|
| **PT-RPT-001** | Success report line count | 485 lines | 485 lines | Exact match | ⏳ |
| **PT-RPT-002** | Failure report line count | 15 lines | 15 lines | Exact match | ⏳ |
| **PT-RPT-003** | Pipe delimiter | `|` separator | `|` separator | Exact match | ⏳ |
| **PT-RPT-004** | Field count | 5 fields | 12 fields | ⚠️ Known difference (configurable) | ⏳ |
| **PT-RPT-005** | Core fields present | REQ_ID, CUST_ID, ACCT_NO, TYPE, STATUS | Same fields present | Core fields match | ⏳ |

---

## 6. Performance Test Scenarios

### 6.1 Throughput Tests

| Test ID | Scenario | Input Size | Target Throughput | Measured Throughput | Status |
|---------|----------|------------|-------------------|---------------------|--------|
| **PERF-001** | Small batch | 100 records | 500 records/sec | TBD | ⏳ |
| **PERF-002** | Medium batch | 1,000 records | 1000 records/sec | TBD | ⏳ |
| **PERF-003** | Large batch | 10,000 records | 1000 records/sec | TBD | ⏳ |
| **PERF-004** | Concurrent batches | 3 x 1000 records | 1000 records/sec (each) | TBD | ⏳ |

### 6.2 Latency Tests

| Test ID | Scenario | Operation | Target Latency | Measured Latency | Status |
|---------|----------|-----------|----------------|------------------|--------|
| **PERF-LAT-001** | Account creation (API) | POST /api/v1/accounts | < 100ms (p50) | TBD | ⏳ |
| **PERF-LAT-002** | Account creation (API) | POST /api/v1/accounts | < 500ms (p99) | TBD | ⏳ |
| **PERF-LAT-003** | Batch job (100 records) | POST /api/v1/batch/... | < 10 seconds | TBD | ⏳ |
| **PERF-LAT-004** | Batch job (1000 records) | POST /api/v1/batch/... | < 60 seconds | TBD | ⏳ |

### 6.3 Resource Utilization

| Test ID | Scenario | Resource | Target | Measured | Status |
|---------|----------|----------|--------|----------|--------|
| **PERF-RES-001** | CPU utilization (ECS) | Batch job (1000 records) | < 70% | TBD | ⏳ |
| **PERF-RES-002** | Memory utilization (ECS) | Batch job (1000 records) | < 80% | TBD | ⏳ |
| **PERF-RES-003** | DB connections | Concurrent API calls (100) | < 50 connections | TBD | ⏳ |
| **PERF-RES-004** | DB CPU utilization | Batch job (10000 records) | < 70% | TBD | ⏳ |

---

## 7. Negative Test Scenarios

### 7.1 Error Handling Tests

| Test ID | Scenario | Trigger | Expected Behavior | Status |
|---------|----------|---------|-------------------|--------|
| **NEG-001** | Database connection failure | Simulate DB down | HTTP 500, error logged, no data loss | ⏳ |
| **NEG-002** | S3 bucket not accessible | Simulate S3 down | HTTP 500, job fails gracefully | ⏳ |
| **NEG-003** | EventBridge unavailable | Simulate EventBridge down | Account created, event in DLQ | ⏳ |
| **NEG-004** | Out of memory | Process 100K records in 1 GB memory | Graceful degradation or OOM error | ⏳ |
| **NEG-005** | Timeout on Core API call | Simulate API timeout (30s) | Retry 3 times, then fail | ⏳ |
| **NEG-006** | Malformed JSON request | Send invalid JSON to API | HTTP 400, clear error message | ⏳ |
| **NEG-007** | SQL injection attempt | `customerId="'; DROP TABLE account;--"` | Parameterized query prevents injection | ⏳ |
| **NEG-008** | XSS attempt | `customerName="<script>alert('XSS')</script>"` | Input sanitized, stored safely | ⏳ |

### 7.2 Resilience Tests

| Test ID | Scenario | Trigger | Expected Behavior | Status |
|---------|----------|---------|-------------------|--------|
| **RES-001** | ECS task killed mid-batch | Kill task at record 50/100 | Spring Batch resumes from last chunk | ⏳ |
| **RES-002** | Database failover (Multi-AZ) | Trigger RDS failover | Automatic reconnect, continue processing | ⏳ |
| **RES-003** | Network partition | Simulate network latency (5s) | Timeout, retry logic kicks in | ⏳ |
| **RES-004** | DynamoDB throttling | Exceed provisioned capacity | Exponential backoff, retry | ⏳ |

---

## 8. Edge Case Test Scenarios

### 8.1 Boundary Value Tests

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **EDGE-001** | DOB exactly 1900-01-01 | `dob="1900-01-01"` | No exception | ⏳ |
| **EDGE-002** | DOB exactly 2099-12-31 | `dob="2099-12-31"` | FAIL (future date) | ⏳ |
| **EDGE-003** | Deposit exactly 0.00 | `deposit=0.00` | No exception | ⏳ |
| **EDGE-004** | Deposit max value | `deposit=9999999999.99` | No exception | ⏳ |
| **EDGE-005** | SAV deposit exactly 500 | `type=SAV, deposit=500` | No exception | ⏳ |
| **EDGE-006** | SAV deposit exactly 499.99 | `type=SAV, deposit=499.99` | FAIL (BV-04) | ⏳ |
| **EDGE-007** | CUR deposit exactly 1000 | `type=CUR, deposit=1000` | No exception | ⏳ |
| **EDGE-008** | CUR deposit exactly 999.99 | `type=CUR, deposit=999.99` | FAIL (BV-04) | ⏳ |

### 8.2 Special Character Tests

| Test ID | Scenario | Input | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **EDGE-CHAR-001** | Unicode customer name | `name="José García"` | Stored and retrieved correctly | ⏳ |
| **EDGE-CHAR-002** | Emoji in name | `name="John 😀 Doe"` | Stored and retrieved correctly | ⏳ |
| **EDGE-CHAR-003** | Name with apostrophe | `name="O'Brien"` | No SQL error | ⏳ |
| **EDGE-CHAR-004** | Name with quotes | `name="John \"The Rock\" Doe"` | Escaped correctly | ⏳ |
| **EDGE-CHAR-005** | Very long name (100 chars) | `name="A" * 100` | Truncated or rejected | ⏳ |

### 8.3 Concurrency Tests

| Test ID | Scenario | Setup | Expected Result | Status |
|---------|----------|-------|-----------------|--------|
| **CONC-001** | Same request ID, 2 threads | Thread 1 and 2 create same requestId | Only 1 succeeds, other gets BV-06 | ⏳ |
| **CONC-002** | 100 concurrent API calls | 100 threads, different requestIds | All succeed | ⏳ |
| **CONC-003** | 3 concurrent batch jobs | 3 jobs processing different files | All complete without conflict | ⏳ |
| **CONC-004** | Account number collision | Force LCG to generate same number | Retry logic handles collision | ⏳ |

---

## 9. Test Data Files

### 9.1 Input File: `input_10_all_valid.dat`

**Description**: 10 records, all valid, should result in 10 success, 0 failures

```
REQ0000000000001 CUST00000001Alice Johnson       US123 Main St        19900515SAVINR000000500000BRANCH    
REQ0000000000002 CUST00000002Bob Smith           IN456 Oak Ave        19850822CURUSD000001000000ONLINE    
REQ0000000000003 CUST00000003Charlie Brown       GB789 Elm Rd         19921130LOAEUR000000000000ATM       
REQ0000000000004 CUST00000004Diana Prince        US321 Pine St        19880310SAVINR000000500000BRANCH    
REQ0000000000005 CUST00000005Eve Adams           CA654 Maple Dr       19950701CURUSD000001500000ONLINE    
REQ0000000000006 CUST00000001Alice Johnson       US123 Main St        19900515CURINR000001000000BRANCH    
REQ0000000000007 CUST00000002Bob Smith           IN456 Oak Ave        19850822SAVINR000000600000BRANCH    
REQ0000000000008 CUST00000003Charlie Brown       GB789 Elm Rd         19921130CURUSD000002000000ONLINE    
REQ0000000000009 CUST00000004Diana Prince        US321 Pine St        19880310LOAINR000000000000BRANCH    
REQ0000000000010 CUST00000005Eve Adams           CA654 Maple Dr       19950701SAVINR000000700000BRANCH    
```

**Expected Output**:
- Success: 10
- Failure: 0
- Success report: 10 lines

---

### 9.2 Input File: `input_100_mixed.dat`

**Description**: 100 records, 80 valid, 20 invalid (various validation failures)

**Breakdown**:
- Records 1-70: Valid (70 success)
- Records 71-75: DV-01 violations (missing REQUEST_ID or CUSTOMER_ID)
- Records 76-80: DV-03 violations (invalid DOB: year < 1900 or > 2099)
- Records 81-85: BV-01 violations (customer not found or inactive)
- Records 86-90: BV-02 violations (blacklisted customers)
- Records 91-95: BV-04 violations (SAV < 500, CUR < 1000)
- Records 96-100: Valid (10 more success)

**Expected Output**:
- Success: 80
- Failure: 20

---

### 9.3 Input File: `input_500_baseline.dat` (COBOL Parity Baseline)

**Description**: 500 records used for COBOL parity testing

**Breakdown** (matches COBOL test data):
- Records 1-485: Valid (485 success)
- Records 486-490: DV-01 violations (5 failures)
- Records 491-495: BV-01 violations (5 failures)
- Records 496-500: BV-04 violations (5 failures)

**Expected Output** (COBOL baseline):
- Success: 485
- Failure: 15

**COBOL Control Totals** (from sample_data/expected_success_report.dat):
```
INPUT=500
SUCCESS=485
FAIL=15
MQ-SUCCESS=485
MQ-FAIL=0
```

---

### 9.4 Reference Data Setup

**SQL Script**: `test-data/reference/customer_master.sql`

```sql
-- Clear existing test data
DELETE FROM acct_owner.account WHERE customer_id LIKE 'CUST%';
DELETE FROM acct_owner.account_audit WHERE customer_id LIKE 'CUST%';
DELETE FROM acct_owner.customer WHERE customer_id LIKE 'CUST%';

-- Insert test customers
INSERT INTO acct_owner.customer (customer_id, customer_name, date_of_birth, country, status, blacklist_flag) VALUES
-- Valid active customers (1-100)
('CUST00000001', 'Alice Johnson',     '1990-05-15', 'US', 'A', 'N'),
('CUST00000002', 'Bob Smith',         '1985-08-22', 'IN', 'A', 'N'),
('CUST00000003', 'Charlie Brown',     '1992-11-30', 'GB', 'A', 'N'),
('CUST00000004', 'Diana Prince',      '1988-03-10', 'US', 'A', 'N'),
('CUST00000005', 'Eve Adams',         '1995-07-01', 'CA', 'A', 'N'),
-- ... (95 more valid customers) ...
('CUST00000100', 'Zara Wilson',       '1991-09-09', 'US', 'A', 'N'),

-- Inactive customers (101-110) - for BV-01 failures
('CUST00000101', 'Frank Miller',      '1980-12-25', 'US', 'I', 'N'),
('CUST00000102', 'Grace Hopper',      '1975-04-18', 'IN', 'I', 'N'),
-- ... (8 more inactive) ...

-- Blacklisted customers (111-120) - for BV-02 failures
('CUST00000111', 'Harry Potter',      '1990-07-31', 'GB', 'A', 'Y'),
('CUST00000112', 'Ivy League',        '1985-09-15', 'US', 'A', 'Y');
-- ... (8 more blacklisted) ...

-- Commit
COMMIT;
```

---

## 10. Test Execution Checklist

### 10.1 Pre-Test Setup

- [ ] **Environment**: Dev environment provisioned and accessible
- [ ] **Database**: Test data loaded (customer_master.sql executed)
- [ ] **S3 Buckets**: Input/reports buckets created and accessible
- [ ] **IAM Roles**: ECS task role has permissions to S3, RDS, EventBridge
- [ ] **Secrets**: Database credentials in Secrets Manager
- [ ] **Application**: Both APIs deployed and healthy (health checks pass)
- [ ] **Test Files**: All test data files uploaded to S3

### 10.2 Unit Test Execution

```bash
# Run all unit tests
cd account-creation-platform
mvn clean test

# Generate coverage report
mvn jacoco:report

# Check coverage threshold (80%)
mvn jacoco:check -Djacoco.coverage.minimum=0.80

# View report
open target/site/jacoco/index.html
```

**Expected Results**:
- [ ] All unit tests pass (100%)
- [ ] Coverage >= 80%
- [ ] No critical SonarQube issues

### 10.3 Integration Test Execution

```bash
# Run integration tests (requires Dev environment)
mvn integration-test -P integration-tests

# Or run specific test class
mvn test -Dtest=CoreAccountApiIntegrationTest
```

**Expected Results**:
- [ ] All integration tests pass (100%)
- [ ] API health checks pass
- [ ] Database queries execute successfully
- [ ] S3 read/write operations work
- [ ] EventBridge events published

### 10.4 Parity Test Execution

```bash
# Upload COBOL baseline data
aws s3 cp test-data/input/input_500_baseline.dat \
  s3://acct-batch-test-input-files/parity-test-$(date +%Y%m%d).dat

# Trigger batch job
curl -X POST http://acct-batch-test-alb.us-east-1.elb.amazonaws.com/api/v1/batch/account-creation \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputFile": "s3://acct-batch-test-input-files/parity-test-20260303.dat",
    "jobName": "parity-test-001"
  }'

# Wait for completion (10 minutes)
# Download reports
aws s3 sync s3://acct-batch-test-reports/parity-test-001/ ./parity-results/

# Compare with COBOL baseline
diff test-data/expected/cobol_baseline/success_report_cobol_500.dat \
     parity-results/success_report_*.dat

# Validate control totals
jq '.successCount' parity-results/control_totals_*.json
# Expected: 485
```

**Expected Results**:
- [ ] Success count: 485 (matches COBOL)
- [ ] Failure count: 15 (matches COBOL)
- [ ] Total: 500 (matches input)
- [ ] Error codes match COBOL for all 15 failures
- [ ] No duplicate account numbers
- [ ] All account numbers pass Luhn validation

### 10.5 Performance Test Execution

```bash
# Run performance test with JMeter or Gatling
# Test 1: 1000 records in < 60 seconds
aws s3 cp test-data/input/input_1000_performance.dat \
  s3://acct-batch-test-input-files/perf-test-1000.dat

# Measure time
START=$(date +%s)
# ... trigger and wait for completion ...
END=$(date +%s)
DURATION=$((END - START))
echo "Duration: $DURATION seconds (target: < 60s)"

# Test 2: Measure latency (p50, p99) for API
# Use Apache Bench or Gatling
ab -n 1000 -c 10 -T 'application/json' -p request.json \
  http://acct-coreapi-test-alb.us-east-1.elb.amazonaws.com/api/v1/accounts
```

**Expected Results**:
- [ ] Throughput >= 1000 records/sec
- [ ] Latency p50 < 100ms
- [ ] Latency p99 < 500ms
- [ ] CPU utilization < 70%
- [ ] Memory utilization < 80%

### 10.6 Test Sign-Off

**Sign-off Criteria**:
- [ ] Unit tests: 100% pass, 80%+ coverage
- [ ] Integration tests: 100% pass
- [ ] Parity tests: 95%+ match with COBOL
- [ ] Performance tests: Meet throughput/latency targets
- [ ] Negative tests: All error scenarios handled gracefully
- [ ] Security tests: No SQL injection, XSS, or sensitive data leakage

**Sign-off Approvals**:
- [ ] Technical Lead: _________________________  Date: ___________
- [ ] QA Lead: _________________________________  Date: ___________
- [ ] Business Owner: __________________________  Date: ___________

---

**End of Document**

---

**Next Steps**:
1. Execute unit tests (Phase 1)
2. Execute integration tests (Phase 2)
3. Execute parity tests (Phase 3)
4. Document results in `07-test-results-parity.md`
5. Prepare for UAT sign-off

**Estimated Testing Duration**: 1-2 weeks
