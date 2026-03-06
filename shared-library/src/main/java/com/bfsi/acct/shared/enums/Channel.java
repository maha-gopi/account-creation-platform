package com.bfsi.acct.shared.enums;

/**
 * Channel enumeration
 * Maps to legacy COBOL CHANNEL field
 */
public enum Channel {
    BRANCH("Branch"),
    WEB("Web Portal"),
    MOBILE("Mobile App"),
    PARTNER("Partner Channel");
    
    private final String description;
    
    Channel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static Channel fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            return Channel.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
