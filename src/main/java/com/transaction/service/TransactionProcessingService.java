package com.transaction.service;

import com.transaction.model.ProcessingResult;
import com.transaction.model.Transaction;
import com.transaction.model.TransactionRequest;
import com.transaction.model.TransactionStatus;
import com.transaction.processor.TransactionProcessor;
import com.transaction.store.AccountBalanceStore;
import com.transaction.store.IdempotencyStore;
import com.transaction.strategy.OutOfOrderStrategy;
import com.transaction.validator.TransactionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Background transaction processing service.
 * Orchestrates the processing of transaction requests with validation,
 * idempotency, out-of-order handling, and retry logic.
 */
public class TransactionProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionProcessingService.class);
    
    private final TransactionValidator validator;
    private final TransactionProcessor processor;
    private final IdempotencyStore idempotencyStore;
    private final AccountBalanceStore accountBalanceStore;
    private final OutOfOrderStrategy outOfOrderStrategy;

    public TransactionProcessingService() {
        this.idempotencyStore = new IdempotencyStore();
        this.accountBalanceStore = new AccountBalanceStore();
        this.outOfOrderStrategy = new OutOfOrderStrategy();
        this.validator = new TransactionValidator();
        this.processor = new TransactionProcessor(idempotencyStore, accountBalanceStore, outOfOrderStrategy);
    }

    /**
     * Process a batch of transaction requests.
     * Returns a processing result with summary statistics.
     */
    public ProcessingResult processBatch(List<TransactionRequest> requests) {
        logger.info("Starting batch processing of {} transactions", requests.size());
        long startTime = System.currentTimeMillis();
        
        ProcessingResult result = new ProcessingResult();
        List<Transaction> processedTransactions = new ArrayList<>();

        for (TransactionRequest request : requests) {
            try {
                // Check for null request
                if (request == null) {
                    logger.error("Null request encountered in batch");
                    result.incrementStatus(TransactionStatus.FAILED);
                    continue;
                }

                // Validate the request
                if (!validator.isValid(request)) {
                    logger.error("Validation failed for request: {}", request.getRequestId());
                    result.incrementStatus(TransactionStatus.FAILED);
                    continue;
                }

                // Process the transaction
                Transaction transaction = processor.process(request);
                processedTransactions.add(transaction);
                
                // Update result statistics
                result.incrementStatus(transaction.getStatus());
                
                logger.info("Transaction {} processed with status: {}", 
                           transaction.getTransactionId(), transaction.getStatus());
                           
            } catch (Exception e) {
                logger.error("Unexpected error processing request: {}", 
                           request != null ? request.getRequestId() : "null", e);
                result.incrementStatus(TransactionStatus.FAILED);
            }
        }

        long endTime = System.currentTimeMillis();
        result.setProcessingTimeMillis(endTime - startTime);
        
        // Log processing summary
        logProcessingSummary(result, processedTransactions);
        
        return result;
    }

    /**
     * Log a detailed processing summary.
     */
    private void logProcessingSummary(ProcessingResult result, List<Transaction> transactions) {
        logger.info("=== Processing Summary ===");
        logger.info("Total Processed: {}", result.getTotalProcessed());
        logger.info("Successful: {}", result.getSuccessCount());
        logger.info("Duplicates: {}", result.getDuplicateCount());
        logger.info("Failed: {}", result.getFailedCount());
        logger.info("Pending: {}", result.getPendingCount());
        logger.info("Processing Time: {} ms", result.getProcessingTimeMillis());
        logger.info("========================");
        
        logger.info("=== Account Balances ===");
        accountBalanceStore.getAllBalances().forEach((accountId, balance) -> {
            logger.info("Account {}: {}", accountId, balance);
        });
        logger.info("========================");
        
        logger.info("=== Transaction Details ===");
        transactions.forEach(tx -> {
            logger.info("TXN: {} | Account: {} | Type: {} | Amount: {} | Status: {} | Retries: {}", 
                       tx.getTransactionId(), tx.getAccountId(), 
                       tx.getTransactionType(), tx.getAmount(), 
                       tx.getStatus(), tx.getRetryCount());
        });
        logger.info("========================");
    }

    /**
     * Get the idempotency store (for testing/inspection).
     */
    public IdempotencyStore getIdempotencyStore() {
        return idempotencyStore;
    }

    /**
     * Get the account balance store (for testing/inspection).
     */
    public AccountBalanceStore getAccountBalanceStore() {
        return accountBalanceStore;
    }

    /**
     * Get the out-of-order strategy (for testing/inspection).
     */
    public OutOfOrderStrategy getOutOfOrderStrategy() {
        return outOfOrderStrategy;
    }

    /**
     * Reset all state (useful for testing).
     */
    public void reset() {
        idempotencyStore.clear();
        accountBalanceStore.clear();
        outOfOrderStrategy.clear();
        logger.info("Service state reset");
    }
}
