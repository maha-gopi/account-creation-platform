package com.bfsi.acct.businessprocess.client;

import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.dto.response.AccountCreationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for Core Account API
 * Circuit breaker and retry configured via Resilience4j
 */
@FeignClient(
    name = "core-account-api",
    url = "${core-account-api.base-url}",
    configuration = FeignClientConfiguration.class
)
public interface CoreAccountClient {
    
    /**
     * Create account via Core Account API
     * @param request Account creation request
     * @return Account creation response with account number or error
     */
    @PostMapping("/api/v1/core/accounts")
    AccountCreationResponse createAccount(@RequestBody AccountCreationRequest request);
}
