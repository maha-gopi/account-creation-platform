package com.bfsi.acct.businessprocess.service;

import com.bfsi.acct.shared.dto.batch.AccountReportRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Dead Letter Queue Service
 * Sends failed records to SQS DLQ for manual investigation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Send failed record to DLQ
     * @param record Failed account record
     * @param errorDetails Additional error context
     */
    public void sendToDLQ(AccountReportRecord record, String errorDetails) {
        try {
            // Configure ObjectMapper to handle LocalDate serialization
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            
            DLQMessage dlqMessage = DLQMessage.builder()
                .requestId(record.getRequestId())
                .customerId(record.getCustomerId())
                .errorCode(record.getErrorCode())
                .errorMessage(record.getErrorMessage())
                .errorDetails(errorDetails)
                .timestamp(System.currentTimeMillis())
                .originalRecord(record)
                .build();
            
            String messageBody = mapper.writeValueAsString(dlqMessage);
            
            // Simulating sending to DLQ by logging
            log.warn("SIMULATED DLQ MESSAGE: {}", messageBody);
            
            log.info("Sent to local DLQ (log) for requestId={}", 
                record.getRequestId());
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ for requestId: {}", 
                record.getRequestId(), e);
            // Don't throw - DLQ failure should not block processing
        }
    }
    
    /**
     * DLQ Message structure
     */
    @lombok.Data
    @lombok.Builder
    private static class DLQMessage {
        private String requestId;
        private String customerId;
        private String errorCode;
        private String errorMessage;
        private String errorDetails;
        private Long timestamp;
        private AccountReportRecord originalRecord;
    }
}
