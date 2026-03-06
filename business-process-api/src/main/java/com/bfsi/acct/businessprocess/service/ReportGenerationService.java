package com.bfsi.acct.businessprocess.service;

import com.bfsi.acct.shared.dto.batch.AccountReportRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Report Generation Service
 * Generates success and failure reports and uploads to S3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {
    @Value("${report.output.dir:./reports}")
    private String reportOutputDir;
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Generate and upload success report to S3
     * Format: REQ_ID|CUST_ID|CUST_NAME|DOB|ACCT_TYPE|CURRENCY|INIT_DEP|CHANNEL|ACCT_NO|STATUS|ERR_CODE|ERR_MSG
     */
    public String generateSuccessReport(List<AccountReportRecord> successRecords, String jobId) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String fileName = String.format("success_report_%s_%s.dat", jobId, timestamp);
        
        try {
            String reportContent = buildReportContent(successRecords);
            uploadToFileSystem(fileName, reportContent);
            
            log.info("Success report written to: {}/{} with {} records", 
                reportOutputDir, fileName, successRecords.size());
            
            return String.format("%s/%s", reportOutputDir, fileName);
            
        } catch (Exception e) {
            log.error("Failed to generate success report for jobId: {}", jobId, e);
            throw new RuntimeException("Report generation failed", e);
        }
    }
    
    /**
     * Generate and upload failure report to S3
     */
    public String generateFailureReport(List<AccountReportRecord> failureRecords, String jobId) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String fileName = String.format("failure_report_%s_%s.dat", jobId, timestamp);
        
        try {
            String reportContent = buildReportContent(failureRecords);
            uploadToFileSystem(fileName, reportContent);
            
            log.info("Failure report written to: {}/{} with {} records", 
                reportOutputDir, fileName, failureRecords.size());
            
            return String.format("%s/%s", reportOutputDir, fileName);
            
        } catch (Exception e) {
            log.error("Failed to generate failure report for jobId: {}", jobId, e);
            throw new RuntimeException("Report generation failed", e);
        }
    }
    
    /**
     * Build pipe-delimited report content
     */
    private String buildReportContent(List<AccountReportRecord> records) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            
            // Write header
            writer.println("REQ_ID|CUST_ID|CUST_NAME|DOB|ACCT_TYPE|CURRENCY|INIT_DEP|CHANNEL|ACCT_NO|STATUS|ERR_CODE|ERR_MSG");
            
            // Write records
            for (AccountReportRecord record : records) {
                writer.println(record.toPipeDelimited());
            }
        }
        
        return baos.toString(StandardCharsets.UTF_8);
    }
    
    /**
     * Write report content to local file system
     */
    private void uploadToFileSystem(String fileName, String content) throws IOException {
        Path dirPath = Paths.get(reportOutputDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        Path filePath = dirPath.resolve(fileName);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }
    
    /**
     * Generate control totals summary
     */
    public String generateControlTotals(int totalRecords, int successCount, int failureCount) {
        StringBuilder summary = new StringBuilder();
        summary.append("\n=== CONTROL TOTALS ===\n");
        summary.append(String.format("Total Input Records:    %d\n", totalRecords));
        summary.append(String.format("Successful Accounts:    %d\n", successCount));
        summary.append(String.format("Failed Records:         %d\n", failureCount));
        summary.append(String.format("Reconciliation Check:   %s\n", 
            (totalRecords == successCount + failureCount) ? "PASS" : "FAIL"));
        summary.append("======================\n");
        
        return summary.toString();
    }
}
