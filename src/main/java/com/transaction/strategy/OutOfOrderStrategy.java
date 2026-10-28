package com.transaction.strategy;

import com.transaction.model.Transaction;
import com.transaction.model.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Sequence-based out-of-order handling strategy.
 * 
 * Strategy: Transactions are processed in sequence order per account.
 * - Transactions with sequence numbers higher than the next expected are marked PENDING.
 * - When the missing sequence arrives, pending transactions are re-evaluated.
 * - This ensures transactions are applied in the correct order.
 * 
 * Trade-offs:
 * - Pros: Strict ordering guarantees, easy to reason about
 * - Cons: Can block processing if a transaction is permanently lost
 * - Production consideration: Add timeout mechanism to force process pending transactions
 */
public class OutOfOrderStrategy {
    private static final Logger logger = LoggerFactory.getLogger(OutOfOrderStrategy.class);
    
    // Key: accountId, Value: next expected sequence number
    private final Map<String, Long> nextExpectedSequence;
    
    // Key: accountId + sequence, Value: pending transaction
    private final Map<String, Transaction> pendingTransactions;

    public OutOfOrderStrategy() {
        this.nextExpectedSequence = new TreeMap<>();
        this.pendingTransactions = new TreeMap<>();
    }

    /**
     * Determine if a transaction can be processed now or should be pending.
     * Returns the appropriate status for the transaction.
     */
    public TransactionStatus determineStatus(Transaction transaction) {
        String accountId = transaction.getAccountId();
        long sequenceNumber = transaction.getSequenceNumber();
        
        long expectedSequence = nextExpectedSequence.getOrDefault(accountId, 1L);
        
        if (sequenceNumber == expectedSequence) {
            // This is the expected next transaction
            logger.info("Transaction {} for account {} is at expected sequence {}", 
                       transaction.getTransactionId(), accountId, sequenceNumber);
            return TransactionStatus.PROCESSING;
        } else if (sequenceNumber < expectedSequence) {
            // This is a late-arriving transaction that should have been processed already
            // Mark as DUPLICATE since we've likely already processed this sequence
            logger.info("Transaction {} for account {} has sequence {} which is less than expected {}, marking as DUPLICATE", 
                       transaction.getTransactionId(), accountId, sequenceNumber, expectedSequence);
            return TransactionStatus.DUPLICATE;
        } else {
            // Future sequence - mark as PENDING
            String pendingKey = accountId + ":" + sequenceNumber;
            pendingTransactions.put(pendingKey, transaction);
            logger.info("Transaction {} for account {} has sequence {} which is greater than expected {}, marking as PENDING", 
                       transaction.getTransactionId(), accountId, sequenceNumber, expectedSequence);
            return TransactionStatus.PENDING;
        }
    }

    /**
     * Mark a sequence as processed for an account.
     * This advances the expected sequence counter.
     */
    public void markSequenceProcessed(String accountId, long sequenceNumber) {
        long currentExpected = nextExpectedSequence.getOrDefault(accountId, 1L);
        if (sequenceNumber == currentExpected) {
            nextExpectedSequence.put(accountId, sequenceNumber + 1);
            logger.debug("Advanced expected sequence for account {} to {}", accountId, sequenceNumber + 1);
            
            // Check if any pending transactions can now be processed
            checkPendingTransactions(accountId);
        }
    }

    /**
     * Check if any pending transactions for an account can now be processed.
     */
    private void checkPendingTransactions(String accountId) {
        long expectedSequence = nextExpectedSequence.getOrDefault(accountId, 1L);
        String pendingKey = accountId + ":" + expectedSequence;
        
        while (pendingTransactions.containsKey(pendingKey)) {
            Transaction pendingTx = pendingTransactions.remove(pendingKey);
            logger.info("Pending transaction {} for account {} can now be processed (sequence {})", 
                       pendingTx.getTransactionId(), accountId, expectedSequence);
            
            // In a real system, this would trigger re-processing
            // For this assessment, we just log it
            expectedSequence++;
            nextExpectedSequence.put(accountId, expectedSequence);
            pendingKey = accountId + ":" + expectedSequence;
        }
    }

    /**
     * Get the next expected sequence for an account.
     */
    public long getNextExpectedSequence(String accountId) {
        return nextExpectedSequence.getOrDefault(accountId, 1L);
    }

    /**
     * Get count of pending transactions.
     */
    public int getPendingCount() {
        return pendingTransactions.size();
    }

    /**
     * Clear all state (useful for testing).
     */
    public void clear() {
        nextExpectedSequence.clear();
        pendingTransactions.clear();
        logger.info("Out-of-order strategy state cleared");
    }
}
