package com.bfsi.acct.core.controller;

import com.bfsi.acct.core.service.AccountCreationService;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.dto.response.AccountCreationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Core Account API REST Controller
 * Handles account creation persistence operations
 */
@RestController
@RequestMapping("/api/v1/core/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    
    private final AccountCreationService accountCreationService;
    
    /**
     * Create new account
     * POST /api/v1/core/accounts
     * 
     * Performs:
     * 1. Idempotency check (DynamoDB cache)
     * 2. Generate account number (LCG + Luhn)
     * 3. Insert into database (account + audit)
     * 4. Publish event to EventBridge
     * 5. Cache in DynamoDB
     */
    @PostMapping
    public ResponseEntity<AccountCreationResponse> createAccount(
            @Valid @RequestBody AccountCreationRequest request) {
        
        log.info("Received account creation request: requestId={}, customerId={}", 
            request.getRequestId(), request.getCustomerId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            AccountCreationResponse response = accountCreationService.createAccount(request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(processingTime);
            
            log.info("Account created successfully: requestId={}, accountNumber={}, processingTime={}ms", 
                request.getRequestId(), response.getAccountNumber(), processingTime);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to create account: requestId={}", request.getRequestId(), e);
            
            AccountCreationResponse errorResponse = AccountCreationResponse.failure(
                request.getRequestId(),
                "TECH-99",
                "Account creation failed: " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/v1/core/accounts/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Core Account API is healthy");
    }
}
