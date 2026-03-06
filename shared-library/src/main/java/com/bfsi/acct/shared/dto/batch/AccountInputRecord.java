package com.bfsi.acct.shared.dto.batch;

import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Channel;
import com.bfsi.acct.shared.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Maps to legacy ACCTINP.cpy copybook (200 bytes fixed-width)
 * Positions documented inline with field definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInputRecord {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    // Position 1-20: REQUEST_ID (PIC X(20))
    private String requestId;
    
    // Position 21-32: CUSTOMER_ID (PIC X(12))
    private String customerId;
    
    // Position 33-72: CUSTOMER_NAME (PIC X(40))
    private String customerName;
    
    // Position 73-80: DOB (PIC 9(8) YYYYMMDD)
    private LocalDate dateOfBirth;
    
    // Position 81-83: ACCT_TYPE (PIC X(03) SAV/CUR/LOA)
    private AccountType accountType;
    
    // Position 84-86: CURRENCY (PIC X(03) INR/USD/EUR)
    private Currency currency;
    
    // Position 87-99: INIT_DEP (PIC 9(11)V99 - 13 digits display)
    private BigDecimal initialDeposit;
    
    // Position 100-101: COUNTRY (PIC X(02))
    private String country;
    
    // Position 102-111: CHANNEL (PIC X(10))
    private Channel channel;
    
    // Position 112-200: FILLER (PIC X(89)) - Ignored
    
    /**
     * Parse from 200-byte fixed-width string
     * @param line Fixed-width record (200 bytes)
     * @return Parsed AccountInputRecord
     * @throws IllegalArgumentException if line length is not 200
     */
    public static AccountInputRecord parseFromFixedWidth(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Input line cannot be null");
        }
        
        if (line.length() != 200) {
            throw new IllegalArgumentException("Invalid record length: " + line.length() + ", expected 200");
        }
        
        AccountInputRecord record = new AccountInputRecord();
        
        // Position 1-20: REQUEST_ID
        record.setRequestId(line.substring(0, 20).trim());
        
        // Position 21-32: CUSTOMER_ID
        record.setCustomerId(line.substring(20, 32).trim());
        
        // Position 33-72: CUSTOMER_NAME
        record.setCustomerName(line.substring(32, 72).trim());
        
        // Position 73-80: DOB (YYYYMMDD)
        String dobStr = line.substring(72, 80).trim();
        if (!dobStr.isEmpty()) {
            try {
                record.setDateOfBirth(LocalDate.parse(dobStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Leave as null, will be caught by validation
                record.setDateOfBirth(null);
            }
        }
        
        // Position 81-83: ACCT_TYPE
        String accountTypeStr = line.substring(80, 83).trim();
        record.setAccountType(AccountType.fromCode(accountTypeStr));
        
        // Position 84-86: CURRENCY
        String currencyStr = line.substring(83, 86).trim();
        record.setCurrency(Currency.fromCode(currencyStr));
        
        // Position 87-99: INIT_DEP (implied 2 decimal places)
        String amountStr = line.substring(86, 99).trim();
        if (!amountStr.isEmpty()) {
            try {
                long amountCents = Long.parseLong(amountStr);
                record.setInitialDeposit(new BigDecimal(amountCents).divide(new BigDecimal("100")));
            } catch (NumberFormatException e) {
                // Leave as null, will be caught by validation
                record.setInitialDeposit(null);
            }
        }
        
        // Position 100-101: COUNTRY
        record.setCountry(line.substring(99, 101).trim());
        
        // Position 102-111: CHANNEL
        String channelStr = line.substring(101, 111).trim();
        record.setChannel(Channel.fromCode(channelStr));
        
        // Position 112-200: FILLER - Ignored
        
        return record;
    }
    
    /**
     * Convert to API request format
     */
    public String toApiRequestJson() {
        return String.format(
            "{\"requestId\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"dateOfBirth\":\"%s\"," +
            "\"accountType\":\"%s\",\"currency\":\"%s\",\"initialDeposit\":%s,\"country\":\"%s\",\"channel\":\"%s\"}",
            requestId, customerId, customerName, 
            dateOfBirth != null ? dateOfBirth.toString() : "",
            accountType != null ? accountType.name() : "",
            currency != null ? currency.name() : "",
            initialDeposit != null ? initialDeposit.toString() : "0",
            country,
            channel != null ? channel.name() : ""
        );
    }
}
