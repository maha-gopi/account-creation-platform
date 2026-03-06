package com.bfsi.acct.shared.enums;

/**
 * Account type enumeration
 * Maps to legacy COBOL ACCT-TYPE field
 */
public enum AccountType {
    SAV("Savings Account"),
    CUR("Current Account"),
    LOA("Loan Account");
    
    private final String description;
    
    AccountType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static AccountType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            return AccountType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
