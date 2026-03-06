package com.bfsi.acct.core.repository;

import com.bfsi.acct.core.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Account Repository
 * JPA repository for Account entity
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    
    /**
     * Find account by request ID (idempotency check)
     */
    Optional<Account> findByRequestId(String requestId);
    
    /**
     * Check if account exists by request ID
     */
    boolean existsByRequestId(String requestId);
    
    /**
     * Count accounts by customer ID
     */
    long countByCustomerId(String customerId);
    
    /**
     * Check if account number exists (for collision detection)
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Account a WHERE a.accountNumber = ?1")
    boolean existsByAccountNumber(String accountNumber);
}
