package com.bfsi.acct.businessprocess.repository;

import com.bfsi.acct.businessprocess.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Account entity (read-only for validations)
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    
    /**
     * Check if account with given REQUEST_ID exists (idempotency check)
     */
    boolean existsByRequestId(String requestId);
}
