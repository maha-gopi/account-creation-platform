package com.bfsi.acct.businessprocess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "kyc_status", nullable = false, length = 20)
    private String kycStatus; // VERIFIED, PENDING, REJECTED

    @Column(name = "blacklist_flag", nullable = false)
    private Character blacklistFlag; // Y or N

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    public String getStatus() {
        return this.kycStatus;
    }
}
