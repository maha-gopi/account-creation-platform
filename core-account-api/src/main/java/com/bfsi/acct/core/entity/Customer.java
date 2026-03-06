package com.bfsi.acct.core.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Customer entity (pre-existing reference data)
 * Read-only for account creation validations
 */
@Entity
@Table(name = "customer", schema = "acct_owner")
@Data
public class Customer {
    
    @Id
    @Column(name = "customer_id", length = 12, nullable = false)
    private String customerId;
    
    @Column(name = "customer_name", length = 40, nullable = false)
    private String customerName;
    
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;
    
    @Column(name = "status", length = 1, nullable = false)
    private Character status; // 'A' = Active, 'I' = Inactive
    
    @Column(name = "blacklist_flag", length = 1, nullable = false)
    private Character blacklistFlag; // 'Y' = Blacklisted, 'N' = Not blacklisted
    
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;
}
