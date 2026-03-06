package com.bfsi.acct.businessprocess.validation.validator;

import com.bfsi.acct.businessprocess.repository.AccountRepository;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BV-06: Duplicate request validation (idempotency check)
 * Check if REQUEST_ID already exists in ACCOUNT table
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicateRequestValidator {
    
    private final AccountRepository accountRepository;
    
    public void validate(AccountCreationRequest request) {
        String requestId = request.getRequestId();
        
        boolean exists = accountRepository.existsByRequestId(requestId);
        
        if (exists) {
            log.warn("BV-06 failed: Duplicate REQUEST_ID {} detected for customerId: {}", 
                requestId, request.getCustomerId());
            throw new BusinessValidationException(
                "BV-06",
                String.format("Duplicate REQUEST_ID: %s already exists", requestId),
                request.getRequestId()
            );
        }
        
        log.debug("BV-06 passed: REQUEST_ID {} is unique", requestId);
    }
}
