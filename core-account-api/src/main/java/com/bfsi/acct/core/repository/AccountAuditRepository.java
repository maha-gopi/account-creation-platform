package com.bfsi.acct.core.repository;

import com.bfsi.acct.core.entity.AccountAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Account Audit Repository
 * JPA repository for AccountAudit entity (immutable audit trail)
 */
@Repository
public interface AccountAuditRepository extends JpaRepository<AccountAudit, Long> {
    
    /**
     * Find all audit records for a request ID
     */
    List<AccountAudit> findByRequestIdOrderByEventTimestampDesc(String requestId);
    
    /**
     * Find all audit records for an account number
     */
    List<AccountAudit> findByAccountNumberOrderByEventTimestampDesc(String accountNumber);
}
