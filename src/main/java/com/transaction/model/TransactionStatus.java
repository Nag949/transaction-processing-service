package com.transaction.model;

/**
 * Transaction status lifecycle enum representing the state of a transaction
 * through its processing journey.
 */
public enum TransactionStatus {
    RECEIVED("Transaction received and queued for processing"),
    PROCESSING("Transaction is currently being processed"),
    PROCESSED("Transaction successfully processed"),
    DUPLICATE("Duplicate transaction detected, not processed"),
    FAILED("Transaction failed after all retry attempts"),
    RETRY_PENDING("Transaction failed transiently, pending retry"),
    PENDING("Transaction pending due to out-of-order sequence");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a terminal state (no further processing expected)
     */
    public boolean isTerminal() {
        return this == PROCESSED || this == DUPLICATE || this == FAILED;
    }

    /**
     * Check if this is a retryable state
     */
    public boolean isRetryable() {
        return this == RETRY_PENDING;
    }
}
