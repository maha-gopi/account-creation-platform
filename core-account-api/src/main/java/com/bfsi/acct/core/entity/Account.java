package com.bfsi.acct.core.entity;

import com.bfsi.acct.shared.enums.AccountStatus;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Channel;
import com.bfsi.acct.shared.enums.Currency;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Account entity - master account table
 */
@Entity
@Table(name = "account", schema = "acct_owner",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_request_id", columnNames = "request_id")
    },
    indexes = {
        @Index(name = "idx_account_customer_id", columnList = "customer_id"),
        @Index(name = "idx_account_open_ts", columnList = "open_ts")
    }
)
@Data
public class Account {
    
    @Id
    @Column(name = "account_no", length = 20, nullable = false)
    private String accountNumber;
    
    @Column(name = "request_id", length = 20, nullable = false, unique = true)
    private String requestId;
    
    @Column(name = "customer_id", length = 12, nullable = false)
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 3, nullable = false)
    private AccountType accountType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;
    
    @Column(name = "open_balance", precision = 13, scale = 2, nullable = false)
    private BigDecimal openBalance;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel_code", length = 10, nullable = false)
    private Channel channelCode;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 1, nullable = false)
    private AccountStatus status;
    
    @CreationTimestamp
    @Column(name = "open_ts", nullable = false, updatable = false)
    private OffsetDateTime openTimestamp;
    
    @Version
    @Column(name = "version")
    private Long version;
}
