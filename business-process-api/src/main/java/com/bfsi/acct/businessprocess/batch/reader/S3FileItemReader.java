package com.bfsi.acct.businessprocess.batch.reader;

import com.bfsi.acct.shared.dto.batch.AccountInputRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * S3 File Item Reader for Spring Batch
 * Reads 200-byte fixed-width records from S3 input file
 */
@Component
@Slf4j
public class S3FileItemReader implements ItemReader<AccountInputRecord> {
    private BufferedReader reader;
    private String currentFilePath;
    private int recordCount = 0;
    
    @Value("${input.dir:./input}")
    private String defaultInputDir;
    
    public S3FileItemReader() {
    }
    
    /**
     * Initialize reader with S3 file path
     * Called before job execution starts
     */
    public void open(String filePath) {
        try {
            this.currentFilePath = filePath;
            
            log.info("Opening local file for reading: {}", currentFilePath);
            
            Path path = Paths.get(currentFilePath);
            if (!Files.exists(path)) {
                // Try resolving against default dir
                path = Paths.get(defaultInputDir, currentFilePath);
            }
            
            this.reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            this.recordCount = 0;
            
            log.info("Local file opened successfully: {}", path.toAbsolutePath());
            
        } catch (Exception e) {
            log.error("Failed to open local file: {}", filePath, e);
            throw new RuntimeException("Failed to open local file", e);
        }
    }
    
    /**
     * Read next record from S3 file
     * Returns null when end of file is reached
     */
    @Override
    public AccountInputRecord read() throws Exception {
        if (reader == null) {
            throw new IllegalStateException("Reader not initialized. Call open() first.");
        }
        
        String line = reader.readLine();
        
        if (line == null) {
            log.info("End of file reached. Total records read: {}", recordCount);
            return null; // Signal end of input
        }
        
        recordCount++;
        
        try {
            AccountInputRecord record = AccountInputRecord.parseFromFixedWidth(line);
            log.debug("Read record {}: requestId={}, customerId={}", 
                recordCount, record.getRequestId(), record.getCustomerId());
            return record;
            
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse record {} at line {}: {}", recordCount, recordCount, e.getMessage());
            // Skip malformed records or throw based on configuration
            throw new RuntimeException("Malformed record at line " + recordCount, e);
        }
    }
    
    /**
     * Close reader and release resources
     * Called after job execution completes
     */
    public void close() {
        if (reader != null) {
            try {
                reader.close();
                log.info("S3 file reader closed. Total records processed: {}", recordCount);
            } catch (Exception e) {
                log.error("Error closing reader", e);
            }
        }
    }
}
