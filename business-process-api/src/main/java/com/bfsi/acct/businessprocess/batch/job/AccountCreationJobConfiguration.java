package com.bfsi.acct.businessprocess.batch.job;

import com.bfsi.acct.businessprocess.batch.listener.AccountCreationSkipListener;
import com.bfsi.acct.businessprocess.batch.listener.JobCompletionListener;
import com.bfsi.acct.businessprocess.batch.processor.AccountValidationProcessor;
import com.bfsi.acct.businessprocess.batch.reader.S3FileItemReader;
import com.bfsi.acct.businessprocess.batch.writer.CoreApiAccountWriter;
import com.bfsi.acct.businessprocess.config.BatchConfiguration;
import com.bfsi.acct.shared.dto.batch.AccountInputRecord;
import com.bfsi.acct.shared.dto.request.AccountCreationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Account Creation Batch Job Configuration
 * Defines Spring Batch job with reader, processor, writer
 */
@Configuration
@RequiredArgsConstructor
public class AccountCreationJobConfiguration {
    
    private final S3FileItemReader reader;
    private final AccountValidationProcessor processor;
    private final CoreApiAccountWriter writer;
    private final JobCompletionListener jobCompletionListener;
    private final AccountCreationSkipListener skipListener;
    private final BatchConfiguration batchConfig;
    
    @Value("${batch.chunk-size:100}")
    private int chunkSize;
    
    @Value("${batch.skip-limit:50}")
    private int skipLimit;
    
    @Value("${batch.retry-limit:3}")
    private int retryLimit;
    
    /**
     * Account Creation Job
     * Single step: read from S3 → validate → write to Core API
     */
    @Bean
    public Job accountCreationJob(JobRepository jobRepository, Step accountCreationStep) {
        return new JobBuilder("accountCreationJob", jobRepository)
            .start(accountCreationStep)
            .listener(jobCompletionListener)
            .build();
    }
    
    /**
     * Account Creation Step
     * Chunk-oriented processing with skip and retry capabilities
     */
    @Bean
    public Step accountCreationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        
        return new StepBuilder("accountCreationStep", jobRepository)
            .<AccountInputRecord, AccountCreationRequest>chunk(chunkSize, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(skipLimit)
            .skip((Class<? extends Throwable>) batchConfig.getSkippableExceptions()[0]) // DataValidationException
            .skip((Class<? extends Throwable>) batchConfig.getSkippableExceptions()[1]) // BusinessValidationException
            .retryLimit(retryLimit)
            .retry((Class<? extends Throwable>) batchConfig.getRetryableExceptions()[0]) // TechnicalException
            .backOffPolicy(exponentialBackOffPolicy())
            .listener(skipListener)
            .build();
    }
    
    /**
     * Exponential backoff policy for retries
     * Initial: 2s, Multiplier: 2x, Max: 10s
     */
    private ExponentialBackOffPolicy exponentialBackOffPolicy() {
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(2000); // 2 seconds
        policy.setMultiplier(2.0); // 2x each retry
        policy.setMaxInterval(10000); // Max 10 seconds
        return policy;
    }
}
