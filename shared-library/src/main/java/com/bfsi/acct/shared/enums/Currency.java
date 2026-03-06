package com.bfsi.acct.shared.enums;

/**
 * Currency enumeration
 * Maps to legacy COBOL CURRENCY field
 */
public enum Currency {
    INR("Indian Rupee"),
    USD("US Dollar"),
    EUR("Euro");
    
    private final String description;
    
    Currency(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static Currency fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            return Currency.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
