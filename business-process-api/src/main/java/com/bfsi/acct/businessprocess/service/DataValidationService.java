package com.bfsi.acct.businessprocess.service;

import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Currency;
import com.bfsi.acct.shared.exception.DataValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Validation Service (DV-01 to DV-05)
 * Validates data format and mandatory fields
 */
@Service
@Slf4j
public class DataValidationService {
    
    /**
     * Execute all data validations
     * @param request Account creation request
     * @throws DataValidationException if any validation fails
     */
    public void validateAll(AccountCreationRequest request) {
        log.debug("Starting data validations for requestId: {}", request.getRequestId());
        
        validateMandatoryFields(request);
        validateAccountType(request);
        validateDateOfBirth(request);
        validateCurrency(request);
        validateInitialDeposit(request);
        
        log.debug("All data validations passed for requestId: {}", request.getRequestId());
    }
    
    /**
     * DV-01: Mandatory fields validation
     * REQUEST_ID and CUSTOMER_ID must be present
     */
    private void validateMandatoryFields(AccountCreationRequest request) {
        if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
            throw new DataValidationException(
                "DV-01",
                "REQUEST_ID is mandatory",
                request.getRequestId()
            );
        }
        
        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            throw new DataValidationException(
                "DV-01",
                "CUSTOMER_ID is mandatory",
                request.getRequestId()
            );
        }
        
        log.debug("DV-01 passed: Mandatory fields present for requestId: {}", request.getRequestId());
    }
    
    /**
     * DV-02: Valid ACCOUNT_TYPE
     * Must be one of: SAV, CUR, LOA
     */
    private void validateAccountType(AccountCreationRequest request) {
        if (request.getAccountType() == null) {
            throw new DataValidationException(
                "DV-02",
                "ACCOUNT_TYPE is mandatory and must be SAV, CUR, or LOA",
                request.getRequestId()
            );
        }
        
        // Enum validation ensures only valid types
        log.debug("DV-02 passed: Valid account type {} for requestId: {}", 
            request.getAccountType(), request.getRequestId());
    }
    
    /**
     * DV-03: Date of birth validation
     * Must be in range 1900-01-01 to 2099-12-31
     * Must be in the past
     */
    private void validateDateOfBirth(AccountCreationRequest request) {
        LocalDate dob = request.getDateOfBirth();
        
        if (dob == null) {
            throw new DataValidationException(
                "DV-03",
                "DATE_OF_BIRTH is mandatory",
                request.getRequestId()
            );
        }
        
        LocalDate minDate = LocalDate.of(1900, 1, 1);
        LocalDate maxDate = LocalDate.of(2099, 12, 31);
        LocalDate today = LocalDate.now();
        
        if (dob.isBefore(minDate) || dob.isAfter(maxDate)) {
            throw new DataValidationException(
                "DV-03",
                String.format("DATE_OF_BIRTH must be between 1900-01-01 and 2099-12-31, got: %s", dob),
                request.getRequestId()
            );
        }
        
        if (dob.isAfter(today)) {
            throw new DataValidationException(
                "DV-03",
                String.format("DATE_OF_BIRTH must be in the past, got: %s", dob),
                request.getRequestId()
            );
        }
        
        log.debug("DV-03 passed: Valid date of birth {} for requestId: {}", 
            dob, request.getRequestId());
    }
    
    /**
     * DV-04: Currency validation
     * Must be one of: INR, USD, EUR
     */
    private void validateCurrency(AccountCreationRequest request) {
        if (request.getCurrency() == null) {
            throw new DataValidationException(
                "DV-04",
                "CURRENCY is mandatory and must be INR, USD, or EUR",
                request.getRequestId()
            );
        }
        
        // Enum validation ensures only valid currencies
        log.debug("DV-04 passed: Valid currency {} for requestId: {}", 
            request.getCurrency(), request.getRequestId());
    }
    
    /**
     * DV-05: Initial deposit validation
     * Must be >= 0 and have max 2 decimal places
     */
    private void validateInitialDeposit(AccountCreationRequest request) {
        BigDecimal amount = request.getInitialDeposit();
        
        if (amount == null) {
            throw new DataValidationException(
                "DV-05",
                "INITIAL_DEPOSIT is mandatory",
                request.getRequestId()
            );
        }
        
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DataValidationException(
                "DV-05",
                String.format("INITIAL_DEPOSIT cannot be negative, got: %s", amount),
                request.getRequestId()
            );
        }
        
        // Check decimal places (max 2)
        if (amount.scale() > 2) {
            throw new DataValidationException(
                "DV-05",
                String.format("INITIAL_DEPOSIT can have max 2 decimal places, got: %s", amount),
                request.getRequestId()
            );
        }
        
        log.debug("DV-05 passed: Valid initial deposit {} for requestId: {}", 
            amount, request.getRequestId());
    }
}
