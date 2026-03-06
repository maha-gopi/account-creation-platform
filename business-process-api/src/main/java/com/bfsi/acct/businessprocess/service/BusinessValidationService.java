package com.bfsi.acct.businessprocess.service;

import com.bfsi.acct.businessprocess.validation.validator.*;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Business Validation Service (BV-01 to BV-06)
 * Validates business rules against database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessValidationService {
    
    private final CustomerExistsValidator customerExistsValidator;
    private final BlacklistValidator blacklistValidator;
    private final ChannelRestrictionValidator channelRestrictionValidator;
    private final MinimumDepositValidator minimumDepositValidator;
    private final DuplicateRequestValidator duplicateRequestValidator;
    
    /**
     * Execute all business validations
     * @param request Account creation request
     * @throws BusinessValidationException if any validation fails
     */
    public void validateAll(AccountCreationRequest request) {
        log.debug("Starting business validations for requestId: {}", request.getRequestId());
        
        // BV-06: Check duplicate request first (fast fail)
        duplicateRequestValidator.validate(request);
        
        // BV-01: Customer exists and is active
        customerExistsValidator.validate(request);
        
        // BV-02: Customer not blacklisted
        blacklistValidator.validate(request);
        
        // BV-03: Channel restrictions
        channelRestrictionValidator.validate(request);
        
        // BV-04: Minimum opening balance
        minimumDepositValidator.validate(request);
        
        log.debug("All business validations passed for requestId: {}", request.getRequestId());
    }
}
