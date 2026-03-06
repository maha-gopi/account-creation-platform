package com.bfsi.acct.core.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Idempotency Cache
 * Replaces AWS DynamoDB for the Render lift and shift migration
 */
@Service
@Slf4j
public class InMemoryIdempotencyCache {
    
    private final Map<String, ValidationRecord> cache = new ConcurrentHashMap<>();
    
    /**
     * Get cached account number for request ID
     * @return accountNumber if cached, null otherwise
     */
    public String getCachedAccountNumber(String requestId) {
        ValidationRecord record = cache.get(requestId);
        if (record != null) {
            log.info("Cache hit for requestId: {} → accountNumber: {}", requestId, record.accountNumber);
            return record.accountNumber;
        }
        log.debug("Cache miss for requestId: {}", requestId);
        return null; // Cache miss
    }
    
    /**
     * Cache account number
     */
    public void cacheAccountNumber(String requestId, String accountNumber, String customerId) {
        ValidationRecord record = new ValidationRecord(accountNumber, customerId);
        cache.put(requestId, record);
        log.info("Cached account number: requestId={}, accountNumber={}", requestId, accountNumber);
    }

    private static class ValidationRecord {
        String accountNumber;

        ValidationRecord(String accountNumber, String customerId) {
            this.accountNumber = accountNumber;
        }
    }
}
