package com.transaction.processor;

import com.transaction.model.Transaction;
import com.transaction.model.TransactionRequest;
import com.transaction.model.TransactionStatus;
import com.transaction.model.TransactionType;
import com.transaction.store.AccountBalanceStore;
import com.transaction.store.IdempotencyStore;
import com.transaction.strategy.OutOfOrderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;

/**
 * Core transaction processor that handles business logic with idempotency,
 * retry logic, and out-of-order handling.
 */
public class TransactionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TransactionProcessor.class);
    
    private static final int MAX_RETRY_COUNT = 2;
    private static final Random random = new Random();
    
    private final IdempotencyStore idempotencyStore;
    private final AccountBalanceStore accountBalanceStore;
    private final OutOfOrderStrategy outOfOrderStrategy;

    // Simulate transient failure for specific transaction IDs
    private static final String SIMULATED_FAILURE_TXN_ID = "TXN-2005";

    public TransactionProcessor(IdempotencyStore idempotencyStore, 
                               AccountBalanceStore accountBalanceStore,
                               OutOfOrderStrategy outOfOrderStrategy) {
        this.idempotencyStore = idempotencyStore;
        this.accountBalanceStore = accountBalanceStore;
        this.outOfOrderStrategy = outOfOrderStrategy;
    }

    /**
     * Process a transaction request with full idempotency and retry handling.
     */
    public Transaction process(TransactionRequest request) {
        logger.info("Processing transaction request: {}", request.getRequestId());
        
        Transaction transaction = new Transaction(request);
        
        // Check for duplicate by requestId
        if (idempotencyStore.isRequestProcessed(request.getRequestId())) {
            Transaction existing = idempotencyStore.getByRequestId(request.getRequestId());
            logger.info("Duplicate request detected: {}, existing status: {}", 
                       request.getRequestId(), existing.getStatus());
            transaction.setStatus(TransactionStatus.DUPLICATE);
            return transaction;
        }
        
        // Check for duplicate by transactionId (business-level deduplication)
        if (idempotencyStore.isTransactionProcessed(request.getTransactionId())) {
            Transaction existing = idempotencyStore.getByTransactionId(request.getTransactionId());
            logger.info("Duplicate transaction detected: {}, existing requestId: {}", 
                       request.getTransactionId(), existing.getRequestId());
            transaction.setStatus(TransactionStatus.DUPLICATE);
            idempotencyStore.storeTransaction(transaction);
            return transaction;
        }
        
        // Apply out-of-order strategy
        TransactionStatus initialStatus = outOfOrderStrategy.determineStatus(transaction);
        transaction.setStatus(initialStatus);
        
        if (initialStatus == TransactionStatus.PENDING) {
            logger.info("Transaction {} marked as PENDING due to out-of-order sequence", 
                       transaction.getTransactionId());
            idempotencyStore.storeTransaction(transaction);
            return transaction;
        }
        
        if (initialStatus == TransactionStatus.DUPLICATE) {
            logger.info("Transaction {} marked as DUPLICATE due to late arrival", 
                       transaction.getTransactionId());
            idempotencyStore.storeTransaction(transaction);
            return transaction;
        }
        
        // Process the transaction
        return processWithRetry(transaction);
    }

    /**
     * Process transaction with retry logic for transient failures.
     */
    private Transaction processWithRetry(Transaction transaction) {
        int attempt = 0;
        
        while (attempt <= MAX_RETRY_COUNT) {
            attempt++;
            transaction.setStatus(TransactionStatus.PROCESSING);
            logger.info("Processing attempt {}/{} for transaction {}", 
                       attempt, MAX_RETRY_COUNT + 1, transaction.getTransactionId());
            
            try {
                boolean success = executeTransaction(transaction);
                
                if (success) {
                    transaction.setStatus(TransactionStatus.PROCESSED);
                    transaction.setProcessedTime(Instant.now());
                    outOfOrderStrategy.markSequenceProcessed(
                        transaction.getAccountId(), 
                        transaction.getSequenceNumber()
                    );
                    idempotencyStore.storeTransaction(transaction);
                    logger.info("Transaction {} processed successfully", transaction.getTransactionId());
                    return transaction;
                }
                
                // Business logic failure (e.g., insufficient funds)
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setErrorMessage("Business logic failure");
                idempotencyStore.storeTransaction(transaction);
                logger.warn("Transaction {} failed: {}", transaction.getTransactionId(), transaction.getErrorMessage());
                return transaction;
                
            } catch (TransientFailureException e) {
                transaction.incrementRetryCount();
                logger.warn("Transient failure for transaction {} (attempt {}): {}", 
                           transaction.getTransactionId(), attempt, e.getMessage());
                
                if (attempt <= MAX_RETRY_COUNT) {
                    transaction.setStatus(TransactionStatus.RETRY_PENDING);
                    // Simulate retry delay
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    transaction.setStatus(TransactionStatus.FAILED);
                    transaction.setErrorMessage("Max retries exceeded: " + e.getMessage());
                    idempotencyStore.storeTransaction(transaction);
                    logger.error("Transaction {} failed after {} retries", 
                                transaction.getTransactionId(), MAX_RETRY_COUNT);
                    return transaction;
                }
            }
        }
        
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setErrorMessage("Unexpected processing error");
        idempotencyStore.storeTransaction(transaction);
        return transaction;
    }

    /**
     * Execute the actual business logic for a transaction.
     * Simulates transient failures for testing retry logic.
     */
    private boolean executeTransaction(Transaction transaction) throws TransientFailureException {
        // Simulate transient failure for specific transaction
        if (transaction.getTransactionId().equals(SIMULATED_FAILURE_TXN_ID)) {
            if (transaction.getRetryCount() == 0) {
                logger.info("Simulating transient failure for {}", transaction.getTransactionId());
                throw new TransientFailureException("Simulated transient failure");
            }
            // Second attempt succeeds
            logger.info("Retry attempt for {} will succeed", transaction.getTransactionId());
        }
        
        // Execute business logic based on transaction type
        switch (transaction.getTransactionType()) {
            case CREDIT:
                accountBalanceStore.credit(transaction.getAccountId(), transaction.getAmount());
                return true;
                
            case DEBIT:
                boolean debitSuccess = accountBalanceStore.debit(
                    transaction.getAccountId(), 
                    transaction.getAmount()
                );
                if (!debitSuccess) {
                    return false; // Business failure, not transient
                }
                return true;
                
            case REVERSAL:
                // For simplicity, reversal is implemented as a credit
                accountBalanceStore.credit(transaction.getAccountId(), transaction.getAmount());
                logger.info("Reversal processed for transaction {}", transaction.getTransactionId());
                return true;
                
            default:
                logger.error("Unknown transaction type: {}", transaction.getTransactionType());
                return false;
        }
    }

    /**
     * Custom exception for transient failures that should trigger retry.
     */
    private static class TransientFailureException extends Exception {
        public TransientFailureException(String message) {
            super(message);
        }
    }
}
