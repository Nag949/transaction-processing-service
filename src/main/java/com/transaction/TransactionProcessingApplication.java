package com.transaction;

import com.transaction.model.TransactionRequest;
import com.transaction.model.TransactionType;
import com.transaction.model.ProcessingResult;
import com.transaction.service.TransactionProcessingService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application class for the Transaction Processing Service.
 * Demonstrates the service with sample test data as specified in the assessment.
 */
public class TransactionProcessingApplication {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Idempotent Transaction Processing Service");
        System.out.println("FDE L2 Assessment");
        System.out.println("========================================\n");
        
        TransactionProcessingService service = new TransactionProcessingService();
        
        // Create test data based on assessment requirements
        List<TransactionRequest> testRequests = createTestData();
        
        System.out.println("Processing " + testRequests.size() + " transactions...\n");
        
        // Process the batch
        ProcessingResult result = service.processBatch(testRequests);
        
        // Print final results
        System.out.println("\n=== Final Processing Results ===");
        System.out.println(result.toString());
        System.out.println("========================================");
        
        // Print account balances
        System.out.println("\n=== Final Account Balances ===");
        service.getAccountBalanceStore().getAllBalances().forEach((accountId, balance) -> {
            System.out.println("Account " + accountId + ": " + balance);
        });
        System.out.println("========================================");
    }
    
    /**
     * Create test data based on L2 assessment requirements.
     * Tests: duplicates, out-of-order, validation failures, and retry scenarios.
     */
    private static List<TransactionRequest> createTestData() {
        List<TransactionRequest> requests = new ArrayList<>();
        
        // Test data from assessment (arriving out of order)
        // 1. TXN-2002 - arrives first but is sequence 2 (should be PENDING)
        requests.add(new TransactionRequest(
            "TXN-2002", "ACC-201", TransactionType.DEBIT, 40.0, "USD", 2, "REQ-2"
        ));
        
        // 2. TXN-2001 - arrives second but is sequence 1 (should be PROCESSED)
        requests.add(new TransactionRequest(
            "TXN-2001", "ACC-201", TransactionType.CREDIT, 100.0, "USD", 1, "REQ-1"
        ));
        
        // 3. TXN-2002 duplicate - same transaction, different requestId (should be DUPLICATE)
        TransactionRequest duplicateRequest = new TransactionRequest(
            "TXN-2002", "ACC-201", TransactionType.DEBIT, 40.0, "USD", 2, "REQ-2-DUP"
        );
        duplicateRequest.setEventTime(Instant.now());
        requests.add(duplicateRequest);
        
        // 4. TXN-2003 - sequence 3 (should be PROCESSED after sequence 2 is handled)
        requests.add(new TransactionRequest(
            "TXN-2003", "ACC-201", TransactionType.CREDIT, 25.0, "USD", 3, "REQ-3"
        ));
        
        // 5. TXN-2004 - negative amount (should fail validation)
        requests.add(new TransactionRequest(
            "TXN-2004", "ACC-202", TransactionType.DEBIT, -10.0, "USD", 4, "REQ-4"
        ));
        
        // 6. TXN-2005 - will simulate transient failure and retry (sequence 1 to avoid out-of-order blocking)
        requests.add(new TransactionRequest(
            "TXN-2005", "ACC-203", TransactionType.CREDIT, 500.0, "USD", 1, "REQ-5"
        ));
        
        return requests;
    }
}
