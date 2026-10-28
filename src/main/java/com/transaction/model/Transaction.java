package com.transaction.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Transaction model representing the processed state of a transaction.
 * Used for persistence and idempotency tracking.
 */
public class Transaction {
    private String transactionId;
    private String accountId;
    private TransactionType transactionType;
    private double amount;
    private String currency;
    private long sequenceNumber;
    private String requestId;
    private TransactionStatus status;
    private Instant eventTime;
    private Instant processedTime;
    private int retryCount;
    private String errorMessage;

    public Transaction() {
    }

    public Transaction(TransactionRequest request) {
        this.transactionId = request.getTransactionId();
        this.accountId = request.getAccountId();
        this.transactionType = request.getTransactionType();
        this.amount = request.getAmount();
        this.currency = request.getCurrency();
        this.sequenceNumber = request.getSequenceNumber();
        this.requestId = request.getRequestId();
        this.eventTime = request.getEventTime();
        this.status = TransactionStatus.RECEIVED;
        this.retryCount = 0;
    }

    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Instant getProcessedTime() {
        return processedTime;
    }

    public void setProcessedTime(Instant processedTime) {
        this.processedTime = processedTime;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", transactionType=" + transactionType +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", requestId='" + requestId + '\'' +
                ", status=" + status +
                ", eventTime=" + eventTime +
                ", processedTime=" + processedTime +
                ", retryCount=" + retryCount +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
