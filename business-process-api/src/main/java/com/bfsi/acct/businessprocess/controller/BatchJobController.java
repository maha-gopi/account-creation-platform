package com.bfsi.acct.businessprocess.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for triggering batch jobs
 */
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchJobController {
    
    private final JobLauncher jobLauncher;
    private final Job accountCreationJob;
    
    /**
     * Trigger account creation batch job
     * POST /api/v1/batch/account-creation
     * 
     * Request body:
     * {
     *   "s3InputFile": "s3://acct-input-files/input_500.dat",
     *   "jobName": "accountCreationJob-20260303-001"
     * }
     */
    @PostMapping("/account-creation")
    public ResponseEntity<Map<String, Object>> triggerAccountCreationJob(
            @RequestBody BatchJobRequest request) {
        
        log.info("Triggering account creation batch job: s3File={}, jobName={}", 
            request.getS3InputFile(), request.getJobName());
        
        try {
            // Create job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("s3InputFile", request.getS3InputFile())
                .addString("jobName", request.getJobName())
                .addLong("timestamp", System.currentTimeMillis()) // Make unique
                .toJobParameters();
            
            // Launch job asynchronously
            JobExecution execution = jobLauncher.run(accountCreationJob, jobParameters);
            
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", execution.getJobId());
            response.put("jobName", request.getJobName());
            response.put("status", execution.getStatus().name());
            response.put("startTime", execution.getStartTime());
            response.put("s3InputFile", request.getS3InputFile());
            
            log.info("Batch job started: jobId={}, status={}", 
                execution.getJobId(), execution.getStatus());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (JobExecutionAlreadyRunningException e) {
            log.error("Job already running", e);
            return ResponseEntity.status(409).body(Map.of("error", "Job already running"));
            
        } catch (JobRestartException e) {
            log.error("Job restart error", e);
            return ResponseEntity.status(400).body(Map.of("error", "Job cannot be restarted: " + e.getMessage()));
            
        } catch (JobInstanceAlreadyCompleteException e) {
            log.error("Job already completed", e);
            return ResponseEntity.status(409).body(Map.of("error", "Job already completed"));
            
        } catch (Exception e) {
            log.error("Failed to launch batch job", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to launch job: " + e.getMessage()));
        }
    }
    
    /**
     * Get job execution status
     * GET /api/v1/batch/status/{jobId}
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobId) {
        // TODO: Implement job status retrieval from JobRepository
        return ResponseEntity.ok(Map.of("message", "Job status endpoint - to be implemented"));
    }
    
    /**
     * Batch Job Request DTO
     */
    @lombok.Data
    public static class BatchJobRequest {
        private String s3InputFile;
        private String jobName;
    }
}
