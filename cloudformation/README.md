# CloudFormation Deployment Guide

## Overview

This directory contains AWS CloudFormation templates for deploying the Account Creation Platform infrastructure. The templates are organized in a modular fashion for easier management and maintenance.

## Stack Structure

The infrastructure is divided into 6 CloudFormation stacks:

```
00-parameters.yaml      → Shared parameters and mappings
01-vpc.yaml            → VPC, subnets, route tables, security groups
02-database.yaml       → RDS PostgreSQL, secrets, alarms
03-storage.yaml        → S3 buckets, DynamoDB table
04-compute.yaml        → ECS clusters, task definitions, ALB
05-messaging.yaml      → EventBridge, SQS queues, SNS topics
```

## Prerequisites

1. **AWS CLI** installed and configured
2. **AWS Account** with appropriate permissions
3. **Docker images** built and pushed to ECR:
   - `business-process-api` (Batch API)
   - `core-account-api` (Core Account API)

## Deployment Order

Deploy the stacks in the following order (due to dependencies):

### Step 1: Deploy Parameters Stack

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-parameters \
  --template-body file://00-parameters.yaml \
  --parameters \
    ParameterKey=Environment,ParameterValue=dev \
    ParameterKey=ProjectName,ParameterValue=acct-batch \
    ParameterKey=CostCenter,ParameterValue=CC-001-DIGITAL-BANKING \
    ParameterKey=Owner,ParameterValue=platform-team@bank.com \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1

# Wait for stack to complete
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-parameters \
  --region us-east-1
```

### Step 2: Deploy VPC Stack

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-vpc \
  --template-body file://01-vpc.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
  --region us-east-1

# Wait for stack to complete (takes ~10-15 minutes due to NAT Gateways)
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-vpc \
  --region us-east-1
```

### Step 3: Deploy Database Stack

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-database \
  --template-body file://02-database.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
    ParameterKey=VpcStackName,ParameterValue=acct-batch-vpc \
    ParameterKey=MasterUsername,ParameterValue=acctadmin \
    ParameterKey=MasterUserPassword,ParameterValue=YourSecurePassword123! \
    ParameterKey=DBName,ParameterValue=acctdb \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1

# Wait for stack to complete (takes ~10-15 minutes)
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-database \
  --region us-east-1
```

### Step 4: Deploy Storage Stack

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-storage \
  --template-body file://03-storage.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
  --region us-east-1

# Wait for stack to complete
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-storage \
  --region us-east-1
```

### Step 5: Deploy Compute Stack

**Note**: Update the Docker image URIs in the command below with your ECR repository URIs.

```bash
# Get your AWS Account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws cloudformation create-stack \
  --stack-name acct-batch-compute \
  --template-body file://04-compute.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
    ParameterKey=VpcStackName,ParameterValue=acct-batch-vpc \
    ParameterKey=DatabaseStackName,ParameterValue=acct-batch-database \
    ParameterKey=StorageStackName,ParameterValue=acct-batch-storage \
    ParameterKey=DockerImageBatch,ParameterValue=${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/acct-batch-business-process-api:latest \
    ParameterKey=DockerImageCoreApi,ParameterValue=${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/acct-batch-core-account-api:latest \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1

# Wait for stack to complete (takes ~10-15 minutes)
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-compute \
  --region us-east-1
```

### Step 6: Deploy Messaging Stack

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-messaging \
  --template-body file://05-messaging.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
  --region us-east-1

# Wait for stack to complete
aws cloudformation wait stack-create-complete \
  --stack-name acct-batch-messaging \
  --region us-east-1
```

## Deployment Script (All Stacks)

You can deploy all stacks using the provided deployment script:

```bash
#!/bin/bash

set -e

# Configuration
ENVIRONMENT="dev"
PROJECT_NAME="acct-batch"
REGION="us-east-1"
DB_PASSWORD="YourSecurePassword123!"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "=========================================="
echo "Deploying Account Creation Platform"
echo "Environment: $ENVIRONMENT"
echo "Region: $REGION"
echo "=========================================="

# Step 1: Parameters Stack
echo "Step 1/6: Deploying Parameters Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-parameters \
  --template-body file://00-parameters.yaml \
  --parameters \
    ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
    ParameterKey=ProjectName,ParameterValue=$PROJECT_NAME \
  --capabilities CAPABILITY_NAMED_IAM \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-parameters \
  --region $REGION

echo "✅ Parameters Stack deployed"

# Step 2: VPC Stack
echo "Step 2/6: Deploying VPC Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-vpc \
  --template-body file://01-vpc.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=${PROJECT_NAME}-parameters \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-vpc \
  --region $REGION

echo "✅ VPC Stack deployed"

# Step 3: Database Stack
echo "Step 3/6: Deploying Database Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-database \
  --template-body file://02-database.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=${PROJECT_NAME}-parameters \
    ParameterKey=VpcStackName,ParameterValue=${PROJECT_NAME}-vpc \
    ParameterKey=MasterUserPassword,ParameterValue=$DB_PASSWORD \
  --capabilities CAPABILITY_NAMED_IAM \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-database \
  --region $REGION

echo "✅ Database Stack deployed"

# Step 4: Storage Stack
echo "Step 4/6: Deploying Storage Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-storage \
  --template-body file://03-storage.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=${PROJECT_NAME}-parameters \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-storage \
  --region $REGION

echo "✅ Storage Stack deployed"

# Step 5: Compute Stack
echo "Step 5/6: Deploying Compute Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-compute \
  --template-body file://04-compute.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=${PROJECT_NAME}-parameters \
    ParameterKey=VpcStackName,ParameterValue=${PROJECT_NAME}-vpc \
    ParameterKey=DatabaseStackName,ParameterValue=${PROJECT_NAME}-database \
    ParameterKey=StorageStackName,ParameterValue=${PROJECT_NAME}-storage \
    ParameterKey=DockerImageBatch,ParameterValue=${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/acct-batch-business-process-api:latest \
    ParameterKey=DockerImageCoreApi,ParameterValue=${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/acct-batch-core-account-api:latest \
  --capabilities CAPABILITY_NAMED_IAM \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-compute \
  --region $REGION

echo "✅ Compute Stack deployed"

# Step 6: Messaging Stack
echo "Step 6/6: Deploying Messaging Stack..."
aws cloudformation create-stack \
  --stack-name ${PROJECT_NAME}-messaging \
  --template-body file://05-messaging.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=${PROJECT_NAME}-parameters \
  --region $REGION

aws cloudformation wait stack-create-complete \
  --stack-name ${PROJECT_NAME}-messaging \
  --region $REGION

echo "✅ Messaging Stack deployed"

echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="

# Display outputs
echo ""
echo "Core API URL:"
aws cloudformation describe-stacks \
  --stack-name ${PROJECT_NAME}-compute \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`CoreApiALBDNSName`].OutputValue' \
  --output text

echo ""
echo "Batch API URL:"
aws cloudformation describe-stacks \
  --stack-name ${PROJECT_NAME}-compute \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`BatchALBDNSName`].OutputValue' \
  --output text

echo ""
echo "Database Endpoint:"
aws cloudformation describe-stacks \
  --stack-name ${PROJECT_NAME}-database \
  --region $REGION \
  --query 'Stacks[0].Outputs[?OutputKey==`DBEndpoint`].OutputValue' \
  --output text
```

Save this as `deploy-all.sh`, make it executable, and run:

```bash
chmod +x deploy-all.sh
./deploy-all.sh
```

## Update Existing Stacks

To update an existing stack:

```bash
aws cloudformation update-stack \
  --stack-name acct-batch-vpc \
  --template-body file://01-vpc.yaml \
  --parameters \
    ParameterKey=ParametersStackName,ParameterValue=acct-batch-parameters \
  --region us-east-1

# Wait for update to complete
aws cloudformation wait stack-update-complete \
  --stack-name acct-batch-vpc \
  --region us-east-1
```

## Delete Stacks

To delete all stacks (in reverse order):

```bash
# Step 1: Delete Messaging Stack
aws cloudformation delete-stack \
  --stack-name acct-batch-messaging \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-messaging \
  --region us-east-1

# Step 2: Delete Compute Stack
aws cloudformation delete-stack \
  --stack-name acct-batch-compute \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-compute \
  --region us-east-1

# Step 3: Delete Storage Stack (empty S3 buckets first!)
# Delete all objects from S3 buckets before deleting stack
aws s3 rm s3://acct-batch-dev-input-files-${AWS_ACCOUNT_ID} --recursive
aws s3 rm s3://acct-batch-dev-reports-${AWS_ACCOUNT_ID} --recursive
aws s3 rm s3://acct-batch-dev-archive-${AWS_ACCOUNT_ID} --recursive
aws s3 rm s3://acct-batch-dev-config-${AWS_ACCOUNT_ID} --recursive

aws cloudformation delete-stack \
  --stack-name acct-batch-storage \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-storage \
  --region us-east-1

# Step 4: Delete Database Stack
aws cloudformation delete-stack \
  --stack-name acct-batch-database \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-database \
  --region us-east-1

# Step 5: Delete VPC Stack
aws cloudformation delete-stack \
  --stack-name acct-batch-vpc \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-vpc \
  --region us-east-1

# Step 6: Delete Parameters Stack
aws cloudformation delete-stack \
  --stack-name acct-batch-parameters \
  --region us-east-1

aws cloudformation wait stack-delete-complete \
  --stack-name acct-batch-parameters \
  --region us-east-1
```

## Verify Deployment

After deployment, verify the infrastructure:

```bash
# List all stacks
aws cloudformation list-stacks \
  --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
  --region us-east-1 \
  --query 'StackSummaries[?starts_with(StackName, `acct-batch`)].{Name:StackName,Status:StackStatus}' \
  --output table

# Get Core API URL
CORE_API_URL=$(aws cloudformation describe-stacks \
  --stack-name acct-batch-compute \
  --region us-east-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`CoreApiALBDNSName`].OutputValue' \
  --output text)

echo "Core API URL: http://${CORE_API_URL}"

# Test health endpoint
curl http://${CORE_API_URL}/actuator/health
```

## Environment-Specific Deployments

To deploy to different environments (dev, test, prod), change the `Environment` parameter:

### Test Environment

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-test-parameters \
  --template-body file://00-parameters.yaml \
  --parameters \
    ParameterKey=Environment,ParameterValue=test \
    ParameterKey=ProjectName,ParameterValue=acct-batch \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1

# Continue with other stacks, updating stack names to include "test"
```

### Production Environment

```bash
aws cloudformation create-stack \
  --stack-name acct-batch-prod-parameters \
  --template-body file://00-parameters.yaml \
  --parameters \
    ParameterKey=Environment,ParameterValue=prod \
    ParameterKey=ProjectName,ParameterValue=acct-batch \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1

# Continue with other stacks, updating stack names to include "prod"
```

## Cost Estimation

- **Dev Environment**: ~$123/month
- **Test Environment**: ~$298/month
- **Prod Environment**: ~$2,525/month

Largest cost drivers:
1. NAT Gateway: $0.045/hour × 3 AZs = $97.20/month
2. RDS PostgreSQL: db.t3.small ($24.82/month) to db.r6g.large ($219.65/month)
3. ECS Fargate: $0.04048/hour for 1 vCPU, 2 GB RAM

## Troubleshooting

### Stack Creation Failed

```bash
# Get failure reason
aws cloudformation describe-stack-events \
  --stack-name acct-batch-vpc \
  --region us-east-1 \
  --query 'StackEvents[?ResourceStatus==`CREATE_FAILED`]' \
  --output table
```

### RDS Not Accessible

Check security groups and network ACLs:

```bash
# Verify RDS security group allows traffic from ECS security group
aws ec2 describe-security-groups \
  --filters Name=tag:Name,Values=acct-batch-dev-rds-sg \
  --region us-east-1
```

### ECS Tasks Not Starting

Check CloudWatch Logs:

```bash
# View logs
aws logs tail /ecs/acct-batch-dev-coreapi \
  --follow \
  --region us-east-1
```

## Additional Resources

- [CloudFormation User Guide](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/)
- [CloudFormation Best Practices](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/best-practices.html)
- [ECS Task Definition Parameters](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html)
- [RDS PostgreSQL Parameters](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.html)
