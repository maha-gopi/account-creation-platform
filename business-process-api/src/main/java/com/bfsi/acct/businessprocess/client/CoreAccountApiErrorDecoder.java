package com.bfsi.acct.businessprocess.client;

import com.bfsi.acct.shared.exception.TechnicalException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom Error Decoder for Core Account API Feign Client
 * Maps HTTP error responses to appropriate exceptions
 */
@Slf4j
public class CoreAccountApiErrorDecoder implements ErrorDecoder {
    
    private final ErrorDecoder defaultDecoder = new Default();
    
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        
        log.warn("Core Account API error: method={}, status={}, reason={}", 
            methodKey, status, response.reason());
        
        switch (status) {
            case 503: // Service Unavailable (transient)
                return new TechnicalException(
                    "TECH-01",
                    "Core Account API is temporarily unavailable",
                    "UNKNOWN",
                    true // Retryable
                );
                
            case 500: // Internal Server Error (may be transient)
                return new TechnicalException(
                    "TECH-02",
                    "Core Account API internal error",
                    "UNKNOWN",
                    true // Retryable
                );
                
            case 408: // Request Timeout (transient)
            case 504: // Gateway Timeout (transient)
                return new TechnicalException(
                    "TECH-03",
                    "Core Account API timeout",
                    "UNKNOWN",
                    true // Retryable
                );
                
            case 400: // Bad Request (permanent)
            case 422: // Unprocessable Entity (permanent)
                return new TechnicalException(
                    "TECH-04",
                    "Core Account API rejected request: " + response.reason(),
                    "UNKNOWN",
                    false // Not retryable
                );
                
            default:
                return defaultDecoder.decode(methodKey, response);
        }
    }
}
