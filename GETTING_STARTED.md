# Getting Started Guide

## Prerequisites Checklist

Before you begin, ensure you have:

- [ ] Java Development Kit (JDK) 17 or higher
- [ ] Apache Maven 3.9 or higher
- [ ] Docker Desktop (or Docker Engine + Docker Compose)
- [ ] Git (for cloning the repository)
- [ ] A text editor or IDE (IntelliJ IDEA, VS Code, Eclipse)
- [ ] 8GB RAM minimum (for running all services locally)
- [ ] 10GB free disk space

---

## Step-by-Step Setup

### 1. Verify Prerequisites

```powershell
# Check Java version
java -version
# Expected: openjdk version "17.0.x" or higher

# Check Maven version
mvn -version
# Expected: Apache Maven 3.9.x

# Check Docker version
docker --version
# Expected: Docker version 20.x or higher

docker-compose --version
# Expected: Docker Compose version 2.x or higher
```

### 2. Clone and Navigate

```powershell
cd account-creation-platform
```

### 3. Start Infrastructure Services

```powershell
# Start PostgreSQL, LocalStack, and pgAdmin
docker-compose up -d

# Verify containers are running
docker-compose ps
```

Expected output:
```
NAME                    SERVICE      STATUS
postgres-db             postgres     Up
localstack-aws          localstack   Up
pgadmin                 pgadmin      Up
```

### 4. Build the Project

```powershell
# Build all modules (parent + 3 child modules)
mvn clean install -DskipTests

# Expected output: BUILD SUCCESS for all 4 modules
```

### 5. Initialize Database (Automatic with Flyway)

The database schema will be created automatically when you start the Core Account API.

### 6. Start Core Account API

**Open a new PowerShell terminal:**

```powershell
cd core-account-api
mvn spring-boot:run
```

Wait for:
```
Started CoreAccountApiApplication in X.XXX seconds
```

The API is now available at: http://localhost:8081

### 7. Start Business Process API

**Open another new PowerShell terminal:**

```powershell
cd business-process-api
mvn spring-boot:run
```

Wait for:
```
Started BusinessProcessApiApplication in X.XXX seconds
```

The API is now available at: http://localhost:8080

---

## Verify Installation

### 1. Health Check - Core API

```powershell
curl http://localhost:8081/api/v1/core/accounts/health
```

Expected response:
```
Core Account API is healthy
```

### 2. Create a Test Account

```powershell
curl -X POST http://localhost:8081/api/v1/core/accounts `
  -H "Content-Type: application/json" `
  -d '{
    "requestId": "TEST000000000001",
    "customerId": "CUST00000001",
    "customerName": "Test User",
    "dateOfBirth": "1990-01-15",
    "accountType": "SAV",
    "currency": "INR",
    "initialDeposit": 5000.00,
    "country": "IN",
    "channel": "BRANCH"
  }'
```

Expected response (201 Created):
```json
{
  "status": "CREATED",
  "requestId": "TEST000000000001",
  "accountNumber": "12312026030312345",
  "errorCode": null,
  "errorMessage": null,
  "processingTimeMs": 125
}
```

### 3. Access pgAdmin (Database UI)

1. Open browser: http://localhost:5050
2. Login:
   - Email: `admin@admin.com`
   - Password: `admin`
3. Add server:
   - Host: `postgres`
   - Port: `5432`
   - Database: `acct_db`
   - Username: `acct_user`
   - Password: `changeme`

### 4. View Test Data

In pgAdmin, run:
```sql
SELECT * FROM acct_owner.customer;
-- Should show 10 test customers (CUST00000001 to CUST00000010)

SELECT * FROM acct_owner.account;
-- Should show the account you just created

SELECT * FROM acct_owner.account_audit;
-- Should show the audit record for account creation
```

---

## Running a Batch Job

### 1. Prepare Input File

Create a test file: `input_test.dat` with 200-byte records:

```
TEST000000000002CUST00000002Jane Doe            IN                  199203125SAVINR00000010000000BRANCH                                                                                                                 
TEST000000000003CUST00000003Bob Smith           US                  198512305CUREUR00000015000000WEB                                                                                                                    
```

### 2. Upload to S3 (LocalStack)

```powershell
# Configure AWS CLI for LocalStack
$env:AWS_ACCESS_KEY_ID="test"
$env:AWS_SECRET_ACCESS_KEY="test"
$env:AWS_DEFAULT_REGION="us-east-1"

# Create bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://acct-input-files

# Upload file
aws --endpoint-url=http://localhost:4566 s3 cp input_test.dat s3://acct-input-files/
```

### 3. Trigger Batch Job

```powershell
curl -X POST http://localhost:8080/api/v1/batch/account-creation `
  -H "Content-Type: application/json" `
  -d '{
    "s3InputFile": "s3://acct-input-files/input_test.dat",
    "jobName": "test-batch-001"
  }'
```

Expected response (202 Accepted):
```json
{
  "jobId": 1,
  "jobName": "test-batch-001",
  "status": "STARTED",
  "startTime": "2026-03-03T10:15:30.123Z",
  "s3InputFile": "s3://acct-input-files/input_test.dat"
}
```

### 4. Monitor Job Execution

**Check application logs** in the Business Process API terminal:
```
INFO  Reading record 1: requestId=TEST000000000002
INFO  Data validations passed for requestId: TEST000000000002
INFO  Business validations passed for requestId: TEST000000000002
INFO  Account created successfully: requestId=TEST000000000002, accountNumber=...
```

### 5. View Reports

```powershell
# List reports
aws --endpoint-url=http://localhost:4566 s3 ls s3://acct-reports/

# Download success report
aws --endpoint-url=http://localhost:4566 s3 cp s3://acct-reports/success_report_1_*.dat ./

# View report
cat success_report_*.dat
```

Expected format:
```
REQ_ID|CUST_ID|CUST_NAME|DOB|ACCT_TYPE|CURRENCY|INIT_DEP|CHANNEL|ACCT_NO|STATUS|ERR_CODE|ERR_MSG
TEST000000000002|CUST00000002|Jane Doe|1992-03-12|SAV|INR|10000.00|BRANCH|12312026030312346|SUCCESS||
```

---

## Troubleshooting

### Problem: Port Already in Use

**Error**: "Port 8080 is already in use"

**Solution**:
```powershell
# Find process using port 8080
netstat -ano | findstr :8080

# Kill process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

### Problem: Docker Containers Won't Start

**Error**: "Cannot connect to Docker daemon"

**Solution**:
1. Ensure Docker Desktop is running
2. Restart Docker Desktop
3. Try again:
```powershell
docker-compose down
docker-compose up -d
```

### Problem: Database Connection Failed

**Error**: "Connection refused: postgres:5432"

**Solution**:
```powershell
# Check PostgreSQL container
docker-compose logs postgres

# Restart PostgreSQL
docker-compose restart postgres

# Wait 10 seconds, then restart application
```

### Problem: Maven Build Fails

**Error**: "Failed to execute goal... compilation failure"

**Solution**:
```powershell
# Clean Maven cache
mvn clean

# Update dependencies
mvn dependency:purge-local-repository

# Rebuild
mvn clean install -DskipTests
```

### Problem: LocalStack S3 Not Working

**Error**: "Unable to connect to AWS services"

**Solution**:
```powershell
# Check LocalStack logs
docker-compose logs localstack

# Restart LocalStack
docker-compose restart localstack

# Recreate S3 buckets
aws --endpoint-url=http://localhost:4566 s3 mb s3://acct-input-files
aws --endpoint-url=http://localhost:4566 s3 mb s3://acct-reports
```

---

## Next Steps

Now that your environment is set up:

1. **Explore the Code**:
   - Review `AccountValidationProcessor.java` for validation logic
   - Check `AccountNumberGenerator.java` for number generation algorithm
   - Examine `AccountCreationService.java` for workflow orchestration

2. **Test Different Scenarios**:
   - Invalid account type (DV-02 error)
   - Inactive customer (BV-01 error)
   - Blacklisted customer (BV-02 error)
   - Duplicate request (BV-06 error)

3. **Review Documentation**:
   - [Implementation Status](./IMPLEMENTATION_STATUS.md) - Progress tracking
   - [API Reference](./API_REFERENCE.md) - All endpoints and formats
   - [Code Design](./03-target-code-design.md) - Technical specifications

4. **Write Tests**:
   - Unit tests for validation services
   - Integration tests for batch job
   - Performance tests for throughput

5. **Deploy to AWS**:
   - Create AWS account
   - Configure Terraform
   - Apply infrastructure
   - Deploy Docker images to ECS

---

## Development Tips

### Hot Reload (DevTools)

Add to your POM (already included):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

Now your application restarts automatically on code changes.

### Debug Mode

**IntelliJ IDEA**:
1. Right-click `CoreAccountApiApplication.java`
2. Select "Debug 'CoreAccountApiApplication'"
3. Set breakpoints in `AccountCreationService.java`

**VS Code**:
1. Install "Java Extension Pack"
2. Open Debug panel (Ctrl+Shift+D)
3. Select "Spring Boot" configuration
4. Press F5

### View Spring Batch Metadata

```sql
-- View all jobs
SELECT * FROM batch_job_instance;

-- View job executions
SELECT * FROM batch_job_execution;

-- View step executions
SELECT * FROM batch_step_execution;

-- Check for failures
SELECT * FROM batch_job_execution WHERE status = 'FAILED';
```

### Tail Logs

```powershell
# Core Account API logs
docker-compose logs -f core-account-api

# Business Process API logs
docker-compose logs -f business-process-api

# PostgreSQL logs
docker-compose logs -f postgres
```

---

## Support

For issues or questions:

1. Check [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md) for known issues
2. Review logs in application terminals
3. Consult [API_REFERENCE.md](./API_REFERENCE.md) for endpoint details
4. Examine error codes in exception classes

---

**Happy Coding! 🚀**

Generated by GitHub Copilot
Date: March 3, 2026
