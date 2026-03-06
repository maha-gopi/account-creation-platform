package com.bfsi.acct.shared.enums;

/**
 * Account status enumeration
 */
public enum AccountStatus {
    A("Active"),
    I("Inactive");
    
    private final String description;
    
    AccountStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
