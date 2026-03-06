package com.bfsi.acct.businessprocess.validation.validator;

import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Channel;
import com.bfsi.acct.shared.exception.BusinessValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BV-03: Channel-based account type restrictions
 * Rule: PARTNER channel cannot open LOA (Loan) accounts
 */
@Component
@Slf4j
public class ChannelRestrictionValidator {
    
    public void validate(AccountCreationRequest request) {
        Channel channel = request.getChannel();
        AccountType accountType = request.getAccountType();
        
        // Rule: PARTNER channel cannot open LOA accounts
        if (channel == Channel.PARTNER && accountType == AccountType.LOA) {
            log.warn("BV-03 failed: PARTNER channel cannot open LOA account for requestId: {}", 
                request.getRequestId());
            throw new BusinessValidationException(
                "BV-03",
                "PARTNER channel is not allowed to open Loan (LOA) accounts",
                request.getRequestId()
            );
        }
        
        log.debug("BV-03 passed: Channel {} is allowed to open {} account for requestId: {}", 
            channel, accountType, request.getRequestId());
    }
}
