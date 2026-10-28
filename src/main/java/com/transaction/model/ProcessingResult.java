package com.transaction.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Processing result containing summary statistics and details of transaction processing.
 */
public class ProcessingResult {
    private int totalProcessed;
    private int successCount;
    private int duplicateCount;
    private int failedCount;
    private int pendingCount;
    private int validationErrorCount;
    private Map<TransactionStatus, Integer> statusCounts;
    private long processingTimeMillis;

    public ProcessingResult() {
        this.statusCounts = new HashMap<>();
        this.processingTimeMillis = 0;
    }

    public void incrementStatus(TransactionStatus status) {
        statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
        totalProcessed++;

        switch (status) {
            case PROCESSED -> successCount++;
            case DUPLICATE -> duplicateCount++;
            case FAILED -> failedCount++;
            case PENDING, RETRY_PENDING -> pendingCount++;
        }
    }

    // Getters and Setters
    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getValidationErrorCount() {
        return validationErrorCount;
    }

    public void setValidationErrorCount(int validationErrorCount) {
        this.validationErrorCount = validationErrorCount;
    }

    public Map<TransactionStatus, Integer> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<TransactionStatus, Integer> statusCounts) {
        this.statusCounts = statusCounts;
    }

    public long getProcessingTimeMillis() {
        return processingTimeMillis;
    }

    public void setProcessingTimeMillis(long processingTimeMillis) {
        this.processingTimeMillis = processingTimeMillis;
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "totalProcessed=" + totalProcessed +
                ", successCount=" + successCount +
                ", duplicateCount=" + duplicateCount +
                ", failedCount=" + failedCount +
                ", pendingCount=" + pendingCount +
                ", validationErrorCount=" + validationErrorCount +
                ", statusCounts=" + statusCounts +
                ", processingTimeMillis=" + processingTimeMillis +
                '}';
    }
}
