package com.bfsi.acct.core.generator;

import com.bfsi.acct.core.repository.AccountRepository;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.exception.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Account Number Generator
 * Generates unique 17-digit account numbers using LCG + Luhn checksum
 * 
 * Format: TYYYYMMDDSSSSSSSC (17 digits)
 * - T (1 digit): Account type (1=SAV, 2=CUR, 3=LOA)
 * - YYYYMMDD (8 digits): Date
 * - SSSSSS (6 digits): Sequence from LCG
 * - C (1 digit): Luhn check digit
 * 
 * Plus 3 digits prefix for total 20 chars (e.g., "123" + 17 digits)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountNumberGenerator {
    
    private final LuhnChecksumCalculator luhnCalculator;
    private final AccountRepository accountRepository;
    
    @Value("${account.generation.lcg-seed:12345}")
    private long lcgSeed;
    
    @Value("${account.generation.max-collision-retries:10}")
    private int maxCollisionRetries;
    
    // LCG constants (from POSIX)
    private static final long LCG_MULTIPLIER = 1103515245L;
    private static final long LCG_INCREMENT = 12345L;
    private static final long LCG_MODULUS = (long) Math.pow(2, 31);
    
    private long currentSeed;
    
    /**
     * Generate unique account number with collision detection
     * @param accountType Type of account (SAV, CUR, LOA)
     * @return 20-character account number
     */
    public String generate(AccountType accountType) {
        // Initialize seed if first time
        if (currentSeed == 0) {
            currentSeed = lcgSeed + System.currentTimeMillis() % 10000;
        }
        
        for (int attempt = 1; attempt <= maxCollisionRetries; attempt++) {
            String accountNumber = generateAccountNumber(accountType);
            
            // Check for collision in database
            boolean exists = accountRepository.existsById(accountNumber);
            
            if (!exists) {
                log.info("Generated unique account number: {} (attempt: {})", accountNumber, attempt);
                return accountNumber;
            }
            
            log.warn("Account number collision detected: {} (attempt: {})", accountNumber, attempt);
            
            // Update seed for next attempt
            currentSeed = nextLCG();
        }
        
        throw new TechnicalException(
            "GEN-01",
            "Failed to generate unique account number after " + maxCollisionRetries + " attempts",
            "UNKNOWN",
            false // Not retryable
        );
    }
    
    /**
     * Generate account number (may have collision)
     */
    private String generateAccountNumber(AccountType accountType) {
        // 1. Account type prefix (1 digit)
        String typePrefix = getAccountTypePrefix(accountType);
        
        // 2. Date component (8 digits: YYYYMMDD)
        String dateComponent = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 3. Sequence from LCG (6 digits)
        long lcgValue = nextLCG();
        String sequenceComponent = String.format("%06d", lcgValue % 1000000);
        
        // 4. Combine without check digit (16 digits)
        String numberWithoutCheckDigit = typePrefix + dateComponent + sequenceComponent;
        
        // 5. Calculate Luhn check digit
        int checkDigit = luhnCalculator.calculateCheckDigit(numberWithoutCheckDigit);
        
        // 6. Final account number (17 digits)
        String accountNumber17 = numberWithoutCheckDigit + checkDigit;
        
        // 7. Add bank prefix (3 digits) for total 20 characters
        String bankPrefix = "123"; // Configurable bank identifier
        String accountNumber = bankPrefix + accountNumber17;
        
        log.debug("Generated account number: type={}, date={}, seq={}, check={}, final={}", 
            accountType, dateComponent, sequenceComponent, checkDigit, accountNumber);
        
        return accountNumber;
    }
    
    /**
     * Get account type prefix
     */
    private String getAccountTypePrefix(AccountType accountType) {
        switch (accountType) {
            case SAV: return "1";
            case CUR: return "2";
            case LOA: return "3";
            default: throw new IllegalArgumentException("Unknown account type: " + accountType);
        }
    }
    
    /**
     * Linear Congruential Generator (LCG)
     * Formula: X(n+1) = (a * X(n) + c) mod m
     * Where: a = 1103515245, c = 12345, m = 2^31
     */
    private long nextLCG() {
        currentSeed = (LCG_MULTIPLIER * currentSeed + LCG_INCREMENT) % LCG_MODULUS;
        return Math.abs(currentSeed);
    }
}
