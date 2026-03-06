package com.bfsi.acct.businessprocess.batch.writer;

import com.bfsi.acct.businessprocess.batch.listener.JobCompletionListener;
import com.bfsi.acct.businessprocess.client.CoreAccountClient;
import com.bfsi.acct.businessprocess.service.DeadLetterQueueService;
import com.bfsi.acct.shared.dto.batch.AccountReportRecord;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.dto.response.AccountCreationResponse;
import com.bfsi.acct.shared.exception.TechnicalException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Core API Account Writer
 * Calls Core Account API to create accounts
 * Applies circuit breaker and retry patterns
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoreApiAccountWriter implements ItemWriter<AccountCreationRequest> {
    
    private final CoreAccountClient coreAccountClient;
    private final DeadLetterQueueService dlqService;
    
    /**
     * Write chunk of validated requests to Core API
     * Each request is processed individually with circuit breaker + retry
     */
    @Override
    public void write(Chunk<? extends AccountCreationRequest> chunk) throws Exception {
        String jobId = getCurrentJobId();
        
        log.info("Writing chunk of {} records to Core API", chunk.size());
        
        for (AccountCreationRequest request : chunk) {
            try {
                AccountCreationResponse response = createAccountWithResilience(request);
                
                if ("CREATED".equals(response.getStatus())) {
                    // Success - add to success report
                    AccountReportRecord successRecord = AccountReportRecord.success(
                        request.getRequestId(),
                        request.getCustomerId(),
                        request.getCustomerName(),
                        request.getDateOfBirth(),
                        request.getAccountType().name(),
                        request.getCurrency().name(),
                        request.getInitialDeposit(),
                        request.getChannel().name(),
                        response.getAccountNumber()
                    );
                    
                    JobCompletionListener.addSuccessRecord(jobId, successRecord);
                    
                    log.info("Account created successfully: requestId={}, accountNumber={}", 
                        request.getRequestId(), response.getAccountNumber());
                    
                } else {
                    // Core API returned error status
                    handleFailure(jobId, request, response.getErrorCode(), response.getErrorMessage());
                }
                
            } catch (TechnicalException e) {
                // Technical error after retries exhausted
                log.error("Technical error creating account for requestId {}: {}", 
                    request.getRequestId(), e.getMessage(), e);
                
                handleFailure(jobId, request, e.getErrorCode(), e.getMessage());
                
                // Send to DLQ for manual investigation
                AccountReportRecord failureRecord = createFailureRecord(request, e.getErrorCode(), e.getMessage());
                dlqService.sendToDLQ(failureRecord, e.getMessage());
                
                // Don't re-throw - continue processing remaining records in chunk
            }
        }
    }
    
    /**
     * Create account with circuit breaker and retry
     * Resilience4j annotations handle retry logic
     */
    @CircuitBreaker(name = "coreAccountApi")
    @Retry(name = "coreAccountApi")
    private AccountCreationResponse createAccountWithResilience(AccountCreationRequest request) {
        return coreAccountClient.createAccount(request);
    }
    
    /**
     * Handle account creation failure
     */
    private void handleFailure(String jobId, AccountCreationRequest request, String errorCode, String errorMessage) {
        AccountReportRecord failureRecord = createFailureRecord(request, errorCode, errorMessage);
        JobCompletionListener.addFailureRecord(jobId, failureRecord);
        
        log.warn("Account creation failed: requestId={}, errorCode={}, errorMessage={}", 
            request.getRequestId(), errorCode, errorMessage);
    }
    
    /**
     * Create failure report record
     */
    private AccountReportRecord createFailureRecord(AccountCreationRequest request, String errorCode, String errorMessage) {
        return AccountReportRecord.failure(
            request.getRequestId(),
            request.getCustomerId(),
            request.getCustomerName(),
            request.getDateOfBirth(),
            request.getAccountType().name(),
            request.getCurrency().name(),
            request.getInitialDeposit(),
            request.getChannel().name(),
            errorCode,
            errorMessage
        );
    }
    
    /**
     * Get current job ID from step context
     */
    private String getCurrentJobId() {
        StepContext stepContext = StepSynchronizationManager.getContext();
        if (stepContext != null) {
            return String.valueOf(stepContext.getStepExecution().getJobExecution().getJobId());
        }
        return "unknown";
    }
}
