package com.transaction.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory account balance store for simulating ledger operations.
 * In production, this would be backed by a proper database with transaction support.
 */
public class AccountBalanceStore {
    private static final Logger logger = LoggerFactory.getLogger(AccountBalanceStore.class);
    
    // Key: accountId, Value: balance
    private final Map<String, Double> accountBalances;

    public AccountBalanceStore() {
        this.accountBalances = new ConcurrentHashMap<>();
    }

    /**
     * Get current balance for an account.
     */
    public double getBalance(String accountId) {
        return accountBalances.getOrDefault(accountId, 0.0);
    }

    /**
     * Credit an account (add funds).
     */
    public void credit(String accountId, double amount) {
        accountBalances.merge(accountId, amount, Double::sum);
        logger.info("Credited account {} with amount {}, new balance: {}", accountId, amount, getBalance(accountId));
    }

    /**
     * Debit an account (remove funds).
     * Returns true if successful, false if insufficient funds.
     */
    public boolean debit(String accountId, double amount) {
        double currentBalance = getBalance(accountId);
        if (currentBalance < amount) {
            logger.warn("Debit failed for account {}: insufficient balance (required: {}, available: {})", 
                       accountId, amount, currentBalance);
            return false;
        }
        accountBalances.put(accountId, currentBalance - amount);
        logger.info("Debited account {} with amount {}, new balance: {}", accountId, amount, getBalance(accountId));
        return true;
    }

    /**
     * Get all account balances.
     */
    public Map<String, Double> getAllBalances() {
        return new ConcurrentHashMap<>(accountBalances);
    }

    /**
     * Clear all balances (useful for testing).
     */
    public void clear() {
        accountBalances.clear();
        logger.info("Account balance store cleared");
    }
}
