package com.bfsi.acct.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Core Account API - Spring Boot Application
 * Handles account persistence and event publishing
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.bfsi.acct.core.repository")
@EntityScan(basePackages = "com.bfsi.acct.core.entity")
public class CoreAccountApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CoreAccountApiApplication.class, args);
    }
}
