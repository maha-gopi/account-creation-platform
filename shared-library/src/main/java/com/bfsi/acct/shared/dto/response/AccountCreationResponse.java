package com.bfsi.acct.shared.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API response for account creation
 * Returned by both Business Process API and Core Account API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreationResponse {
    
    private String requestId;
    private String accountNumber;
    private String status; // "CREATED" or "FAILED"
    private String errorCode;
    private String errorMessage;
    private Long processingTimeMs;
    
    /**
     * Factory method for successful response
     */
    public static AccountCreationResponse success(String requestId, String accountNumber) {
        return AccountCreationResponse.builder()
            .requestId(requestId)
            .accountNumber(accountNumber)
            .status("CREATED")
            .build();
    }
    
    /**
     * Factory method for failure response
     */
    public static AccountCreationResponse failure(String requestId, String errorCode, String errorMessage) {
        return AccountCreationResponse.builder()
            .requestId(requestId)
            .status("FAILED")
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
