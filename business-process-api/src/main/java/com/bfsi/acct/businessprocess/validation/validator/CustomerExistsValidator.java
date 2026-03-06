package com.bfsi.acct.businessprocess.validation.validator;

import com.bfsi.acct.businessprocess.repository.CustomerRepository;
import com.bfsi.acct.businessprocess.entity.Customer;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * BV-01: Customer must exist and be active
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerExistsValidator {
    
    private final CustomerRepository customerRepository;
    
    public void validate(AccountCreationRequest request) {
        String customerId = request.getCustomerId();
        
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        
        if (customerOpt.isEmpty()) {
            log.warn("BV-01 failed: Customer {} not found for requestId: {}", 
                customerId, request.getRequestId());
            throw new BusinessValidationException(
                "BV-01",
                String.format("Customer %s does not exist", customerId),
                request.getRequestId()
            );
        }
        
        Customer customer = customerOpt.get();
        
        if (!"VERIFIED".equals(customer.getStatus())) {
            log.warn("BV-01 failed: Customer {} is inactive (status: {}) for requestId: {}", 
                customerId, customer.getStatus(), request.getRequestId());
            throw new BusinessValidationException(
                "BV-01",
                String.format("Customer %s is not active (status: %s)", customerId, customer.getStatus()),
                request.getRequestId()
            );
        }
        
        log.debug("BV-01 passed: Customer {} exists and is active for requestId: {}", 
            customerId, request.getRequestId());
    }
}
