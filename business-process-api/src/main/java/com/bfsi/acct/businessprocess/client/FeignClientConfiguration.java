package com.bfsi.acct.businessprocess.client;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign Client Configuration
 * Configures timeouts and error handling
 */
@Configuration
public class FeignClientConfiguration {
    
    /**
     * Request timeout configuration
     * Connect timeout: 5 seconds
     * Read timeout: 30 seconds
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            5, TimeUnit.SECONDS,  // Connect timeout
            30, TimeUnit.SECONDS, // Read timeout
            true                  // Follow redirects
        );
    }
    
    /**
     * Feign logging level (for debugging)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; // NONE, BASIC, HEADERS, FULL
    }
    
    /**
     * Custom error decoder
     * Maps HTTP error responses to exceptions
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CoreAccountApiErrorDecoder();
    }
}
