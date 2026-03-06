package com.bfsi.acct.core.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Luhn Checksum Calculator (Mod-10 Algorithm)
 * ISO/IEC 7812-1 standard for validation of identification numbers
 * 
 * Algorithm:
 * 1. Starting from rightmost digit (excluding check digit), double every second digit
 * 2. If doubling results in two digits, add them together
 * 3. Sum all digits
 * 4. Check digit = (10 - (sum mod 10)) mod 10
 */
@Component
@Slf4j
public class LuhnChecksumCalculator {
    
    /**
     * Calculate Luhn check digit for given number
     * @param numberWithoutCheckDigit Number without check digit (e.g., 16 digits)
     * @return Check digit (0-9)
     */
    public int calculateCheckDigit(String numberWithoutCheckDigit) {
        if (numberWithoutCheckDigit == null || numberWithoutCheckDigit.isEmpty()) {
            throw new IllegalArgumentException("Input number cannot be null or empty");
        }
        
        int sum = 0;
        boolean alternate = false;
        
        // Traverse from right to left
        for (int i = numberWithoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(numberWithoutCheckDigit.charAt(i));
            
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("Invalid digit at position " + i + ": " + numberWithoutCheckDigit.charAt(i));
            }
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit / 10) + (digit % 10); // Add digits: 12 → 1+2=3
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        
        log.debug("Calculated Luhn check digit for {}: checkDigit={}, sum={}", 
            numberWithoutCheckDigit, checkDigit, sum);
        
        return checkDigit;
    }
    
    /**
     * Validate number with Luhn check digit
     * @param numberWithCheckDigit Complete number including check digit
     * @return true if valid, false otherwise
     */
    public boolean validate(String numberWithCheckDigit) {
        if (numberWithCheckDigit == null || numberWithCheckDigit.length() < 2) {
            return false;
        }
        
        String numberPart = numberWithCheckDigit.substring(0, numberWithCheckDigit.length() - 1);
        int actualCheckDigit = Character.getNumericValue(numberWithCheckDigit.charAt(numberWithCheckDigit.length() - 1));
        int calculatedCheckDigit = calculateCheckDigit(numberPart);
        
        return actualCheckDigit == calculatedCheckDigit;
    }
}
