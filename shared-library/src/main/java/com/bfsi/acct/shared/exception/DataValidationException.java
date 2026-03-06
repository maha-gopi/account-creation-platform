package com.bfsi.acct.shared.exception;

/**
 * Data validation exception (DV-* errors)
 * These are permanent failures and should NOT be retried
 */
public class DataValidationException extends AccountCreationException {
    
    public DataValidationException(String errorCode, String message, String requestId) {
        super(errorCode, message, requestId);
    }
    
    @Override
    public boolean isRetryable() {
        return false;
    }
}
