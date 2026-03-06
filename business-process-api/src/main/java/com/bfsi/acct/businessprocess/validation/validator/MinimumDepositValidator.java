package com.bfsi.acct.businessprocess.validation.validator;

import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * BV-04: Minimum opening balance validation
 * Rules:
 * - SAV (Savings): Minimum 500
 * - CUR (Current): Minimum 1000
 * - LOA (Loan): No minimum (can be 0)
 */
@Component
@Slf4j
public class MinimumDepositValidator {
    
    private static final BigDecimal MIN_SAVINGS = new BigDecimal("500.00");
    private static final BigDecimal MIN_CURRENT = new BigDecimal("1000.00");
    
    public void validate(AccountCreationRequest request) {
        AccountType accountType = request.getAccountType();
        BigDecimal initialDeposit = request.getInitialDeposit();
        
        if (accountType == AccountType.SAV) {
            if (initialDeposit.compareTo(MIN_SAVINGS) < 0) {
                log.warn("BV-04 failed: SAV account requires minimum {} but got {} for requestId: {}", 
                    MIN_SAVINGS, initialDeposit, request.getRequestId());
                throw new BusinessValidationException(
                    "BV-04",
                    String.format("Savings account requires minimum opening balance of %s, got: %s", 
                        MIN_SAVINGS, initialDeposit),
                    request.getRequestId()
                );
            }
        } else if (accountType == AccountType.CUR) {
            if (initialDeposit.compareTo(MIN_CURRENT) < 0) {
                log.warn("BV-04 failed: CUR account requires minimum {} but got {} for requestId: {}", 
                    MIN_CURRENT, initialDeposit, request.getRequestId());
                throw new BusinessValidationException(
                    "BV-04",
                    String.format("Current account requires minimum opening balance of %s, got: %s", 
                        MIN_CURRENT, initialDeposit),
                    request.getRequestId()
                );
            }
        }
        // LOA has no minimum, skip validation
        
        log.debug("BV-04 passed: Initial deposit {} meets minimum for {} account, requestId: {}", 
            initialDeposit, accountType, request.getRequestId());
    }
}
