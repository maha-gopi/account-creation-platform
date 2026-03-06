package com.bfsi.acct.core.service;

import com.bfsi.acct.core.entity.Account;
import com.bfsi.acct.core.entity.AccountAudit;
import com.bfsi.acct.core.event.AccountCreatedEventPublisher;
import com.bfsi.acct.core.generator.AccountNumberGenerator;
import com.bfsi.acct.core.idempotency.InMemoryIdempotencyCache;
import com.bfsi.acct.core.repository.AccountAuditRepository;
import com.bfsi.acct.core.repository.AccountRepository;
import com.bfsi.acct.shared.dto.event.AccountCreatedEvent;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.dto.response.AccountCreationResponse;
import com.bfsi.acct.shared.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account Creation Service
 * Orchestrates account creation workflow:
 * 1. Check idempotency cache (In-Memory)
 * 2. Generate unique account number (LCG + Luhn)
 * 3. Insert account + audit (PostgreSQL transaction)
 * 4. Publish event (Local)
 * 5. Update idempotency cache
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCreationService {
    
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountRepository accountRepository;
    private final AccountAuditRepository accountAuditRepository;
    private final AccountCreatedEventPublisher eventPublisher;
    private final InMemoryIdempotencyCache idempotencyCache;
    
    /**
     * Create account with full workflow
     * 
     * Transaction Boundary: ONE account creation = ONE transaction
     * Isolation Level: SERIALIZABLE (prevents phantom reads during collision check)
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public AccountCreationResponse createAccount(AccountCreationRequest request) {
        String requestId = request.getRequestId();
        
        log.info("Creating account for requestId={}, customerId={}", requestId, request.getCustomerId());
        
        // Step 1: Check idempotency cache (In-Memory, outside transaction)
        String cachedAccountNumber = idempotencyCache.getCachedAccountNumber(requestId);
        if (cachedAccountNumber != null) {
            log.info("Duplicate request detected, returning cached account: {}", cachedAccountNumber);
            return AccountCreationResponse.success(requestId, cachedAccountNumber);
        }
        
        try {
            // Step 2: Generate unique account number (with collision retry)
            String accountNumber = accountNumberGenerator.generate(request.getAccountType());
            
            // Step 3: Insert Account (within transaction)
            Account account = mapToEntity(request, accountNumber);
            accountRepository.save(account);
            
            log.info("Account persisted: accountNumber={}, requestId={}", accountNumber, requestId);
            
            // Step 4: Insert AccountAudit (within same transaction)
            AccountAudit audit = AccountAudit.success(requestId, accountNumber);
            accountAuditRepository.save(audit);
            
            // COMMIT happens here (end of method)
            
            // Step 5: Publish event locally (AFTER commit)
            try {
                AccountCreatedEvent.Payload payload = AccountCreatedEvent.Payload.builder()
                    .requestId(requestId)
                    .accountNumber(account.getAccountNumber())
                    .customerId(account.getCustomerId())
                    .accountType(account.getAccountType().name())
                    .currency(account.getCurrency().name())
                    .openBalance(account.getOpenBalance())
                    .channel(account.getChannelCode().name())
                    .status(account.getStatus().name())
                    .openTimestamp(account.getOpenTimestamp())
                    .build();
                    
                AccountCreatedEvent event = AccountCreatedEvent.from(payload, requestId);
                eventPublisher.publishAccountCreated(event);
            } catch (Exception e) {
                log.error("Failed to publish event for account {}, event will be lost", accountNumber, e);
                // Do NOT rollback transaction - account is already committed
            }
            
            // Step 6: Store in idempotency cache (7-day TTL)
            idempotencyCache.cacheAccountNumber(requestId, accountNumber, request.getCustomerId());
            
            return AccountCreationResponse.success(requestId, accountNumber);
            
        } catch (DataIntegrityViolationException e) {
            // Duplicate request_id detected (unique constraint violation)
            log.warn("Duplicate requestId={} detected, fetching existing account", requestId);
            
            Account existingAccount = accountRepository.findByRequestId(requestId)
                .orElseThrow(() -> new RuntimeException("Inconsistent state: constraint violation but no record found"));
            
            return AccountCreationResponse.success(requestId, existingAccount.getAccountNumber());
        }
    }
    
    /**
     * Audit failure (separate transaction to avoid rollback)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void auditFailure(String requestId, String errorCode, String errorText) {
        AccountAudit audit = AccountAudit.failure(requestId, errorCode, errorText);
        accountAuditRepository.save(audit);
        log.info("Audit failure recorded: requestId={}, errorCode={}", requestId, errorCode);
    }
    
    /**
     * Map AccountCreationRequest to Account entity
     */
    private Account mapToEntity(AccountCreationRequest request, String accountNumber) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setRequestId(request.getRequestId());
        account.setCustomerId(request.getCustomerId());
        account.setAccountType(request.getAccountType());
        account.setCurrency(request.getCurrency());
        account.setOpenBalance(request.getInitialDeposit());
        account.setChannelCode(request.getChannel());
        account.setStatus(AccountStatus.A); // Active
        // openTimestamp set by @CreationTimestamp
        return account;
    }
}
