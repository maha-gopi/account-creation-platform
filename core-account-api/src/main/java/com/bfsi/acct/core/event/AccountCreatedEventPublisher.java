package com.bfsi.acct.core.event;

import com.bfsi.acct.shared.dto.event.AccountCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Event Publisher
 * Simulates publishing AccountCreated events (replaced AWS EventBridge)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedEventPublisher {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Publish AccountCreated event locally
     */
    public void publishAccountCreated(AccountCreatedEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            
            log.info("Simulating Published AccountCreated event: eventId={}, correlationId={}, json={}", 
                event.getEventId(), event.getCorrelationId(), eventJson);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }
}
