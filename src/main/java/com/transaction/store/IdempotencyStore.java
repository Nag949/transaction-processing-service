package com.transaction.store;

import com.transaction.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store for tracking processed transactions.
 * In production, this would be backed by a database or distributed cache.
 * Uses ConcurrentHashMap for thread-safe operations.
 */
public class IdempotencyStore {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyStore.class);
    
    // Key: requestId, Value: Transaction
    private final Map<String, Transaction> processedRequests;
    
    // Key: transactionId, Value: Transaction (for business-level deduplication)
    private final Map<String, Transaction> processedTransactions;

    public IdempotencyStore() {
        this.processedRequests = new ConcurrentHashMap<>();
        this.processedTransactions = new ConcurrentHashMap<>();
    }

    /**
     * Check if a request has already been processed by requestId.
     */
    public boolean isRequestProcessed(String requestId) {
        return processedRequests.containsKey(requestId);
    }

    /**
     * Check if a transaction has already been processed by transactionId.
     */
    public boolean isTransactionProcessed(String transactionId) {
        return processedTransactions.containsKey(transactionId);
    }

    /**
     * Store a processed transaction.
     */
    public void storeTransaction(Transaction transaction) {
        if (transaction.getRequestId() != null) {
            processedRequests.put(transaction.getRequestId(), transaction);
            logger.debug("Stored request with requestId: {}", transaction.getRequestId());
        }
        if (transaction.getTransactionId() != null) {
            processedTransactions.put(transaction.getTransactionId(), transaction);
            logger.debug("Stored transaction with transactionId: {}", transaction.getTransactionId());
        }
    }

    /**
     * Retrieve a transaction by requestId.
     */
    public Transaction getByRequestId(String requestId) {
        return processedRequests.get(requestId);
    }

    /**
     * Retrieve a transaction by transactionId.
     */
    public Transaction getByTransactionId(String transactionId) {
        return processedTransactions.get(transactionId);
    }

    /**
     * Update an existing transaction (e.g., status change after retry).
     */
    public void updateTransaction(Transaction transaction) {
        if (transaction.getRequestId() != null && processedRequests.containsKey(transaction.getRequestId())) {
            processedRequests.put(transaction.getRequestId(), transaction);
        }
        if (transaction.getTransactionId() != null && processedTransactions.containsKey(transaction.getTransactionId())) {
            processedTransactions.put(transaction.getTransactionId(), transaction);
        }
        logger.debug("Updated transaction: {}", transaction.getTransactionId());
    }

    /**
     * Get the total count of processed requests.
     */
    public int getProcessedRequestCount() {
        return processedRequests.size();
    }

    /**
     * Get the total count of processed transactions.
     */
    public int getProcessedTransactionCount() {
        return processedTransactions.size();
    }

    /**
     * Clear all stored data (useful for testing).
     */
    public void clear() {
        processedRequests.clear();
        processedTransactions.clear();
        logger.info("Idempotency store cleared");
    }
}
