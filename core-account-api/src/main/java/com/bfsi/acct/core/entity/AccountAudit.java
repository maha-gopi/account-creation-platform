package com.bfsi.acct.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Account Audit Entity
 * Immutable audit trail for all account creation attempts
 */
@Entity
@Table(name = "account_audit", schema = "acct_owner",
    indexes = {
        @Index(name = "idx_audit_request_id", columnList = "request_id"),
        @Index(name = "idx_audit_event_ts", columnList = "event_ts"),
        @Index(name = "idx_audit_event_type", columnList = "event_type")
    }
)
@Data
public class AccountAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;
    
    @Column(name = "request_id", length = 20, nullable = false)
    private String requestId;
    
    @Column(name = "account_no", length = 20, nullable = false)
    private String accountNumber;
    
    @Column(name = "event_type", length = 20, nullable = false)
    private String eventType;
    
    @CreationTimestamp
    @Column(name = "event_ts", nullable = false, updatable = false)
    private OffsetDateTime eventTimestamp;
    
    @Column(name = "result_code", length = 12, nullable = false)
    private String resultCode;
    
    @Column(name = "result_text", length = 60, nullable = false)
    private String resultText;
    
    /**
     * Factory method for successful account creation
     */
    public static AccountAudit success(String requestId, String accountNumber) {
        AccountAudit audit = new AccountAudit();
        audit.setRequestId(requestId);
        audit.setAccountNumber(accountNumber);
        audit.setEventType("ACCOUNT_CREATED");
        audit.setResultCode("OK");
        audit.setResultText("Account created successfully");
        return audit;
    }
    
    /**
     * Factory method for failed account creation
     */
    public static AccountAudit failure(String requestId, String errorCode, String errorText) {
        AccountAudit audit = new AccountAudit();
        audit.setRequestId(requestId);
        audit.setAccountNumber(""); // No account created
        audit.setEventType("ACCOUNT_CREATE_FAIL");
        audit.setResultCode(errorCode);
        audit.setResultText(errorText.length() > 60 ? errorText.substring(0, 60) : errorText);
        return audit;
    }
}
