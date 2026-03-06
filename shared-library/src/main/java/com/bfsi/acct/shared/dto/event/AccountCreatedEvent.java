package com.bfsi.acct.shared.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Account Created Event
 * Published to EventBridge when account is successfully created
 * Schema version: 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {
    
    private static final String VERSION = "1.0";
    private static final String EVENT_TYPE = "ACCOUNT_CREATED";
    private static final String SOURCE = "core-account-api";
    
    private String version; // "1.0"
    
    private String eventId; // UUID v4
    
    private String eventType; // "ACCOUNT_CREATED"
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime timestamp; // ISO 8601
    
    private String source; // "core-account-api"
    
    private String correlationId; // requestId for tracing
    
    private Payload payload;
    
    /**
     * Event payload
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private String requestId;
        private String accountNumber;
        private String customerId;
        private String accountType; // SAV, CUR, LOA
        private String currency; // INR, USD, EUR
        private BigDecimal openBalance;
        private String channel; // BRANCH, WEB, MOBILE, PARTNER
        private String status; // A (Active)
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private OffsetDateTime openTimestamp;
    }
    
    /**
     * Factory method to create event
     */
    public static AccountCreatedEvent from(Payload payload, String requestId) {
        return AccountCreatedEvent.builder()
            .version(VERSION)
            .eventId(UUID.randomUUID().toString())
            .eventType(EVENT_TYPE)
            .timestamp(OffsetDateTime.now())
            .source(SOURCE)
            .correlationId(requestId)
            .payload(payload)
            .build();
    }
}
