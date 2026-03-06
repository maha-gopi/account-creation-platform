package com.bfsi.acct.businessprocess.validation.validator;

import com.bfsi.acct.businessprocess.repository.CustomerRepository;
import com.bfsi.acct.businessprocess.entity.Customer;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BV-02: Customer must not be blacklisted
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlacklistValidator {
    
    private final CustomerRepository customerRepository;
    
    public void validate(AccountCreationRequest request) {
        String customerId = request.getCustomerId();
        
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessValidationException(
                "BV-02",
                String.format("Customer %s not found", customerId),
                request.getRequestId()
            ));
        
        if (customer.getBlacklistFlag() == 'Y') {
            log.warn("BV-02 failed: Customer {} is blacklisted for requestId: {}", 
                customerId, request.getRequestId());
            throw new BusinessValidationException(
                "BV-02",
                String.format("Customer %s is blacklisted and cannot open new accounts", customerId),
                request.getRequestId()
            );
        }
        
        log.debug("BV-02 passed: Customer {} is not blacklisted for requestId: {}", 
            customerId, request.getRequestId());
    }
}
