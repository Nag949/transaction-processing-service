package com.transaction.model;

/**
 * Transaction type enum representing the type of financial transaction.
 */
public enum TransactionType {
    CREDIT("Add funds to account"),
    DEBIT("Remove funds from account"),
    REVERSAL("Reverse a previous transaction");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
