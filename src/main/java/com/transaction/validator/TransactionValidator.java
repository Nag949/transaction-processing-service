package com.transaction.validator;

import com.transaction.model.TransactionRequest;
import com.transaction.model.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for transaction requests.
 * Ensures all mandatory fields are present and valid before processing.
 */
public class TransactionValidator {
    private static final Logger logger = LoggerFactory.getLogger(TransactionValidator.class);

    /**
     * Validate a transaction request.
     * Returns list of validation errors. Empty list means valid.
     */
    public List<String> validate(TransactionRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Transaction request cannot be null");
            return errors;
        }

        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            errors.add("transactionId is required");
        }

        if (request.getAccountId() == null || request.getAccountId().trim().isEmpty()) {
            errors.add("accountId is required");
        }

        if (request.getTransactionType() == null) {
            errors.add("transactionType is required");
        }

        if (request.getAmount() <= 0) {
            errors.add("amount must be positive");
        }

        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("currency is required");
        }

        if (request.getSequenceNumber() < 0) {
            errors.add("sequenceNumber must be non-negative");
        }

        if (request.getRequestId() == null || request.getRequestId().trim().isEmpty()) {
            errors.add("requestId is required");
        }

        // Additional business validation
        if (request.getTransactionType() == TransactionType.DEBIT && request.getAmount() < 0) {
            errors.add("DEBIT transactions cannot have negative amount");
        }

        if (!errors.isEmpty()) {
            logger.warn("Validation failed for request {}: {}", request.getRequestId(), errors);
        } else {
            logger.debug("Validation passed for request: {}", request.getRequestId());
        }

        return errors;
    }

    /**
     * Quick validation check - returns true if valid, false otherwise.
     */
    public boolean isValid(TransactionRequest request) {
        return validate(request).isEmpty();
    }
}
