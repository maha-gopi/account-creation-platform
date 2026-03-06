package com.bfsi.acct.businessprocess.entity;

import com.bfsi.acct.shared.enums.AccountStatus;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Channel;
import com.bfsi.acct.shared.enums.Currency;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "account")
@Data
@NoArgsConstructor
public class Account {

    @Id
    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "open_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_code", nullable = false, length = 20)
    private Channel channelCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "open_timestamp", nullable = false, updatable = false)
    private OffsetDateTime openTimestamp;
}
