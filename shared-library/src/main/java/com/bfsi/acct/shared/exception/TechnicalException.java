package com.bfsi.acct.shared.exception;

import lombok.Getter;

/**
 * Technical exception (TECH-* errors)
 * May be transient or permanent depending on the error type
 */
@Getter
public class TechnicalException extends AccountCreationException {
    
    private final boolean transientError;
    
    public TechnicalException(String errorCode, String message, String requestId, boolean transientError) {
        super(errorCode, message, requestId);
        this.transientError = transientError;
    }
    
    public TechnicalException(String errorCode, String message, String requestId, boolean transientError, Throwable cause) {
        super(errorCode, message, requestId, cause);
        this.transientError = transientError;
    }
    
    @Override
    public boolean isRetryable() {
        return transientError;
    }
}
