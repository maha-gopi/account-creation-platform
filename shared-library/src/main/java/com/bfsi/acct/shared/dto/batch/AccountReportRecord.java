package com.bfsi.acct.shared.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Report output record (pipe-delimited format)
 * Maps to legacy ACCTRPT.cpy copybook (300 bytes)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReportRecord {
    
    private String requestId;
    private String customerId;
    private String customerName;
    private LocalDate dateOfBirth;
    private String accountType;
    private String currency;
    private BigDecimal initialDeposit;
    private String channel;
    private String accountNumber;  // Populated on success
    private String status;         // "SUCCESS" or "FAILED"
    private String errorCode;      // DV-*, BV-*, TECH-*, etc.
    private String errorMessage;   // Human-readable error
    
    /**
     * Convert to pipe-delimited format for report file
     * Format: REQ_ID|CUST_ID|CUST_NAME|DOB|ACCT_TYPE|CURRENCY|INIT_DEP|CHANNEL|ACCT_NO|STATUS|ERR_CODE|ERR_MSG
     */
    public String toPipeDelimited() {
        return String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
            requestId != null ? requestId : "",
            customerId != null ? customerId : "",
            customerName != null ? customerName : "",
            dateOfBirth != null ? dateOfBirth.toString() : "",
            accountType != null ? accountType : "",
            currency != null ? currency : "",
            initialDeposit != null ? initialDeposit.toString() : "",
            channel != null ? channel : "",
            accountNumber != null ? accountNumber : "",
            status != null ? status : "FAILED",
            errorCode != null ? errorCode : "",
            errorMessage != null ? errorMessage : ""
        );
    }
    
    /**
     * Create success report record
     */
    public static AccountReportRecord success(
            String requestId, 
            String customerId, 
            String customerName,
            LocalDate dateOfBirth,
            String accountType,
            String currency,
            BigDecimal initialDeposit,
            String channel,
            String accountNumber) {
        
        return AccountReportRecord.builder()
            .requestId(requestId)
            .customerId(customerId)
            .customerName(customerName)
            .dateOfBirth(dateOfBirth)
            .accountType(accountType)
            .currency(currency)
            .initialDeposit(initialDeposit)
            .channel(channel)
            .accountNumber(accountNumber)
            .status("SUCCESS")
            .errorCode("")
            .errorMessage("")
            .build();
    }
    
    /**
     * Create failure report record
     */
    public static AccountReportRecord failure(
            String requestId, 
            String customerId, 
            String customerName,
            LocalDate dateOfBirth,
            String accountType,
            String currency,
            BigDecimal initialDeposit,
            String channel,
            String errorCode,
            String errorMessage) {
        
        return AccountReportRecord.builder()
            .requestId(requestId)
            .customerId(customerId)
            .customerName(customerName)
            .dateOfBirth(dateOfBirth)
            .accountType(accountType)
            .currency(currency)
            .initialDeposit(initialDeposit)
            .channel(channel)
            .accountNumber("")
            .status("FAILED")
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
