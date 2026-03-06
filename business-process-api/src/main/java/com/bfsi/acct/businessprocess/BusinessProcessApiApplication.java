package com.bfsi.acct.businessprocess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Business Process API - Spring Boot Application
 * Handles batch processing and business validations
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaRepositories(basePackages = "com.bfsi.acct.businessprocess.repository")
@EntityScan(basePackages = "com.bfsi.acct.core.entity")
public class BusinessProcessApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BusinessProcessApiApplication.class, args);
    }
}
