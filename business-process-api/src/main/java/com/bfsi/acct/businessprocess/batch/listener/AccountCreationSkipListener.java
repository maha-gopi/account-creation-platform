package com.bfsi.acct.businessprocess.batch.listener;

import com.bfsi.acct.shared.dto.batch.AccountReportRecord;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import com.bfsi.acct.shared.exception.DataValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.stereotype.Component;

/**
 * Skip Listener for handling skipped records
 * Captures validation failures and adds to failure report
 */
@Component
@Slf4j
public class AccountCreationSkipListener implements SkipListener<Object, Object> {
    
    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped record during READ phase: {}", t.getMessage());
    }
    
    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        log.warn("Skipped record during PROCESS phase: item={}, error={}", 
            item, t.getMessage());
        
        String jobId = getCurrentJobId();
        
        if (t instanceof DataValidationException) {
            DataValidationException ex = (DataValidationException) t;
            AccountReportRecord failureRecord = AccountReportRecord.failure(
                ex.getRequestId(),
                "", // customerId not available yet
                "",
                null,
                "",
                "",
                null,
                "",
                ex.getErrorCode(),
                ex.getMessage()
            );
            JobCompletionListener.addFailureRecord(jobId, failureRecord);
            
        } else if (t instanceof BusinessValidationException) {
            BusinessValidationException ex = (BusinessValidationException) t;
            AccountReportRecord failureRecord = AccountReportRecord.failure(
                ex.getRequestId(),
                "", // customerId from item
                "",
                null,
                "",
                "",
                null,
                "",
                ex.getErrorCode(),
                ex.getMessage()
            );
            JobCompletionListener.addFailureRecord(jobId, failureRecord);
        }
    }
    
    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.warn("Skipped record during WRITE phase: item={}, error={}", 
            item, t.getMessage());
    }
    
    private String getCurrentJobId() {
        StepContext stepContext = StepSynchronizationManager.getContext();
        if (stepContext != null) {
            return String.valueOf(stepContext.getStepExecution().getJobExecution().getJobId());
        }
        return "unknown";
    }
}
