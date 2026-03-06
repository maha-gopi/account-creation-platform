package com.bfsi.acct.shared.exception;

/**
 * Business validation exception (BV-* errors)
 * These are permanent failures and should NOT be retried
 */
public class BusinessValidationException extends AccountCreationException {
    
    public BusinessValidationException(String errorCode, String message, String requestId) {
        super(errorCode, message, requestId);
    }
    
    @Override
    public boolean isRetryable() {
        return false;
    }
}
