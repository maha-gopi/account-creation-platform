package com.bfsi.acct.businessprocess.config;

import com.bfsi.acct.shared.exception.BusinessValidationException;
import com.bfsi.acct.shared.exception.DataValidationException;
import com.bfsi.acct.shared.exception.TechnicalException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Spring Batch Configuration
 * Configures JobRepository, TransactionManager, and retry/skip policies
 */
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfiguration {
    
    /**
     * Job Repository configuration (uses PostgreSQL for metadata)
     */
    @Bean
    public JobRepository jobRepository(DataSource dataSource, PlatformTransactionManager transactionManager) throws Exception {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);
        factory.setIsolationLevelForCreate("ISOLATION_SERIALIZABLE");
        factory.setTablePrefix("BATCH_"); // Spring Batch metadata tables
        factory.afterPropertiesSet();
        return factory.getObject();
    }
    
    /**
     * Transaction Manager for batch operations
     */
    @Bean
    public PlatformTransactionManager batchTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * Get skippable exception classes
     */
    public Class<?>[] getSkippableExceptions() {
        return new Class<?>[] {
            DataValidationException.class,
            BusinessValidationException.class
        };
    }
    
    /**
     * Get retryable exception classes
     */
    public Class<?>[] getRetryableExceptions() {
        return new Class<?>[] {
            TechnicalException.class
        };
    }
}
