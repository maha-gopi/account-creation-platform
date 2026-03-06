package com.bfsi.acct.shared.dto.request;

import com.bfsi.acct.shared.enums.AccountType;
import com.bfsi.acct.shared.enums.Channel;
import com.bfsi.acct.shared.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * API request for account creation (real-time or batch)
 * Used by Business Process API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreationRequest {
    
    @NotBlank(message = "DV-01: REQUEST_ID is mandatory")
    @Size(max = 20, message = "REQUEST_ID must be max 20 characters")
    private String requestId;
    
    @NotBlank(message = "DV-01: CUSTOMER_ID is mandatory")
    @Size(max = 12, message = "CUSTOMER_ID must be max 12 characters")
    private String customerId;
    
    @Size(max = 40, message = "CUSTOMER_NAME must be max 40 characters")
    private String customerName;
    
    @NotNull(message = "DV-03: DATE_OF_BIRTH is mandatory")
    @Past(message = "DV-03: DATE_OF_BIRTH must be in the past")
    private LocalDate dateOfBirth;
    
    @NotNull(message = "DV-02: ACCOUNT_TYPE is mandatory")
    private AccountType accountType;
    
    @NotNull(message = "DV-04: CURRENCY is mandatory")
    private Currency currency;
    
    @NotNull(message = "DV-05: INITIAL_DEPOSIT is mandatory")
    @DecimalMin(value = "0.00", message = "DV-05: INITIAL_DEPOSIT cannot be negative")
    @Digits(integer = 11, fraction = 2, message = "INITIAL_DEPOSIT precision: 11 digits, 2 decimals")
    private BigDecimal initialDeposit;
    
    @Size(max = 2, message = "COUNTRY must be 2 characters (ISO code)")
    private String country;
    
    @NotNull(message = "CHANNEL is mandatory")
    private Channel channel;
}
