package com.bfsi.acct.businessprocess.controller;

import com.bfsi.acct.shared.dto.response.AccountCreationResponse;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import com.bfsi.acct.shared.exception.DataValidationException;
import com.bfsi.acct.shared.exception.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global Exception Handler for Business Process API
 * Converts exceptions to appropriate HTTP responses
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle data validation exceptions (400 Bad Request)
     */
    @ExceptionHandler(DataValidationException.class)
    public ResponseEntity<AccountCreationResponse> handleDataValidation(DataValidationException ex) {
        log.warn("Data validation failed: requestId={}, code={}, message={}", 
            ex.getRequestId(), ex.getErrorCode(), ex.getMessage());
        
        AccountCreationResponse response = AccountCreationResponse.failure(
            ex.getRequestId(),
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle business validation exceptions (422 Unprocessable Entity)
     */
    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<AccountCreationResponse> handleBusinessValidation(BusinessValidationException ex) {
        log.warn("Business validation failed: requestId={}, code={}, message={}", 
            ex.getRequestId(), ex.getErrorCode(), ex.getMessage());
        
        AccountCreationResponse response = AccountCreationResponse.failure(
            ex.getRequestId(),
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }
    
    /**
     * Handle technical exceptions (500 or 503)
     */
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<AccountCreationResponse> handleTechnical(TechnicalException ex) {
        log.error("Technical error: requestId={}, code={}, transient={}", 
            ex.getRequestId(), ex.getErrorCode(), ex.isRetryable(), ex);
        
        AccountCreationResponse response = AccountCreationResponse.failure(
            ex.getRequestId(),
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        HttpStatus status = ex.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Handle unexpected exceptions (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AccountCreationResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        
        AccountCreationResponse response = AccountCreationResponse.failure(
            "UNKNOWN",
            "TECH-99",
            "An unexpected error occurred: " + ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
