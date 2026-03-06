package com.bfsi.acct.businessprocess.batch.listener;

import com.bfsi.acct.businessprocess.service.ReportGenerationService;
import com.bfsi.acct.shared.dto.batch.AccountReportRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Job Completion Listener
 * Generates reports and control totals after batch job completes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobCompletionListener implements JobExecutionListener {
    
    private final ReportGenerationService reportGenerationService;
    
    // Thread-safe storage for success/failure records during job execution
    private static final ConcurrentHashMap<String, List<AccountReportRecord>> SUCCESS_RECORDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<AccountReportRecord>> FAILURE_RECORDS = new ConcurrentHashMap<>();
    
    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobId = String.valueOf(jobExecution.getJobId());
        SUCCESS_RECORDS.put(jobId, new ArrayList<>());
        FAILURE_RECORDS.put(jobId, new ArrayList<>());
        
        log.info("Starting batch job: jobId={}, jobName={}", 
            jobId, jobExecution.getJobInstance().getJobName());
    }
    
    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobId = String.valueOf(jobExecution.getJobId());
        BatchStatus status = jobExecution.getStatus();
        
        log.info("Batch job completed: jobId={}, status={}, exitCode={}", 
            jobId, status, jobExecution.getExitStatus().getExitCode());
        
        List<AccountReportRecord> successRecords = SUCCESS_RECORDS.getOrDefault(jobId, new ArrayList<>());
        List<AccountReportRecord> failureRecords = FAILURE_RECORDS.getOrDefault(jobId, new ArrayList<>());
        
        int totalRecords = successRecords.size() + failureRecords.size();
        
        // Generate reports
        if (!successRecords.isEmpty()) {
            String successReportPath = reportGenerationService.generateSuccessReport(successRecords, jobId);
            jobExecution.getExecutionContext().put("successReportPath", successReportPath);
        }
        
        if (!failureRecords.isEmpty()) {
            String failureReportPath = reportGenerationService.generateFailureReport(failureRecords, jobId);
            jobExecution.getExecutionContext().put("failureReportPath", failureReportPath);
        }
        
        // Generate control totals
        String controlTotals = reportGenerationService.generateControlTotals(
            totalRecords, 
            successRecords.size(), 
            failureRecords.size()
        );
        
        log.info(controlTotals);
        
        // Store metrics in job execution context
        jobExecution.getExecutionContext().putInt("totalRecords", totalRecords);
        jobExecution.getExecutionContext().putInt("successCount", successRecords.size());
        jobExecution.getExecutionContext().putInt("failureCount", failureRecords.size());
        
        // Clean up storage
        SUCCESS_RECORDS.remove(jobId);
        FAILURE_RECORDS.remove(jobId);
    }
    
    /**
     * Add success record (called from ItemWriter)
     */
    public static void addSuccessRecord(String jobId, AccountReportRecord record) {
        SUCCESS_RECORDS.computeIfAbsent(jobId, k -> new ArrayList<>()).add(record);
    }
    
    /**
     * Add failure record (called from ItemProcessor or SkipListener)
     */
    public static void addFailureRecord(String jobId, AccountReportRecord record) {
        FAILURE_RECORDS.computeIfAbsent(jobId, k -> new ArrayList<>()).add(record);
    }
}
