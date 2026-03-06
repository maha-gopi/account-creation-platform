package com.bfsi.acct.shared.exception;

import lombok.Getter;

/**
 * Base exception for account creation operations
 */
@Getter
public abstract class AccountCreationException extends RuntimeException {
    
    private final String errorCode;
    private final String requestId;
    
    public AccountCreationException(String errorCode, String message, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }
    
    public AccountCreationException(String errorCode, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }
    
    /**
     * Indicates if this error is transient and should be retried
     */
    public abstract boolean isRetryable();
}
