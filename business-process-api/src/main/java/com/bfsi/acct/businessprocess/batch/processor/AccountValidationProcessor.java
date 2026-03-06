package com.bfsi.acct.businessprocess.batch.processor;

import com.bfsi.acct.businessprocess.service.BusinessValidationService;
import com.bfsi.acct.businessprocess.service.DataValidationService;
import com.bfsi.acct.shared.dto.batch.AccountInputRecord;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import com.bfsi.acct.shared.exception.DataValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Account Validation Processor
 * Applies DV-* and BV-* validations to each input record
 * Transforms AccountInputRecord to AccountCreationRequest
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountValidationProcessor implements ItemProcessor<AccountInputRecord, AccountCreationRequest> {
    
    private final DataValidationService dataValidationService;
    private final BusinessValidationService businessValidationService;
    
    /**
     * Process and validate each input record
     * Returns null to skip record (triggers SkipListener)
     * Throws exception to retry or skip based on configuration
     */
    @Override
    public AccountCreationRequest process(AccountInputRecord item) throws Exception {
        String requestId = item.getRequestId();
        
        log.debug("Processing record: requestId={}, customerId={}", 
            requestId, item.getCustomerId());
        
        // Convert input record to API request
        AccountCreationRequest request = mapToRequest(item);
        
        try {
            // Step 1: Data Validations (DV-01 to DV-05)
            dataValidationService.validateAll(request);
            log.debug("Data validations passed for requestId: {}", requestId);
            
            // Step 2: Business Validations (BV-01 to BV-06)
            businessValidationService.validateAll(request);
            log.debug("Business validations passed for requestId: {}", requestId);
            
            // All validations passed - proceed to writer
            return request;
            
        } catch (DataValidationException e) {
            log.warn("Data validation failed for requestId {}: {} - {}", 
                requestId, e.getErrorCode(), e.getMessage());
            // Re-throw to trigger skip listener
            throw e;
            
        } catch (BusinessValidationException e) {
            log.warn("Business validation failed for requestId {}: {} - {}", 
                requestId, e.getErrorCode(), e.getMessage());
            // Re-throw to trigger skip listener
            throw e;
        }
    }
    
    /**
     * Map AccountInputRecord to AccountCreationRequest
     */
    private AccountCreationRequest mapToRequest(AccountInputRecord input) {
        return AccountCreationRequest.builder()
            .requestId(input.getRequestId())
            .customerId(input.getCustomerId())
            .customerName(input.getCustomerName())
            .dateOfBirth(input.getDateOfBirth())
            .accountType(input.getAccountType())
            .currency(input.getCurrency())
            .initialDeposit(input.getInitialDeposit())
            .country(input.getCountry())
            .channel(input.getChannel())
            .build();
    }
}
