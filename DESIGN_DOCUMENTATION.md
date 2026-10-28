# Design Documentation: Idempotent Background Transaction Processing Service

## 1. Architecture Overview

### 1.1 System Architecture

The service follows a layered architecture pattern with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│         TransactionProcessingApplication                │
│                  (Main Entry Point)                     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│         TransactionProcessingService                    │
│              (Orchestration Layer)                      │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
┌───────▼──────┐ ┌──▼──────┐ ┌───▼──────────┐
│   Validator  │ │Processor│ │   Stores     │
└──────────────┘ └─────────┘ └──────────────┘
                               │
                    ┌──────────┼──────────┐
                    │          │          │
              ┌─────▼──┐ ┌────▼────┐ ┌───▼──────┐
              │Idempot-│ │ Account │ │ Out-of-  │
              │  ency  │ │ Balance │ │  Order   │
              │  Store │ │  Store  │ │Strategy  │
              └────────┘ └─────────┘ └──────────┘
```

### 1.2 Component Responsibilities

- **TransactionProcessingApplication**: Main entry point, creates test data and demonstrates the service
- **TransactionProcessingService**: Orchestrates the processing workflow, manages lifecycle
- **TransactionValidator**: Validates input requests before processing
- **TransactionProcessor**: Executes business logic with retry and idempotency handling
- **IdempotencyStore**: Tracks processed requests and transactions for deduplication
- **AccountBalanceStore**: Manages account balances (simulated ledger)
- **OutOfOrderStrategy**: Implements sequence-based ordering logic

### 1.3 Package Structure

```
com.transaction
├── model/                    # Domain models
│   ├── Transaction.java
│   ├── TransactionRequest.java
│   ├── TransactionStatus.java
│   ├── TransactionType.java
│   └── ProcessingResult.java
├── service/                  # Service layer
│   └── TransactionProcessingService.java
├── processor/                # Business logic
│   └── TransactionProcessor.java
├── store/                    # Persistence (in-memory)
│   ├── IdempotencyStore.java
│   └── AccountBalanceStore.java
├── validator/                # Input validation
│   └── TransactionValidator.java
├── strategy/                 # Out-of-order handling
│   └── OutOfOrderStrategy.java
└── TransactionProcessingApplication.java
```

---

## 2. Idempotency Design

### 2.1 Idempotency Mechanism

The service implements **dual-level idempotency**:

1. **Request-level idempotency**: Uses `requestId` to detect duplicate API requests
2. **Transaction-level idempotency**: Uses `transactionId` to detect duplicate business transactions

### 2.2 Idempotency Store Implementation

```java
// Key: requestId, Value: Transaction
private final Map<String, Transaction> processedRequests;

// Key: transactionId, Value: Transaction
private final Map<String, Transaction> processedTransactions;
```

**Design Rationale**:
- `requestId` ensures the same request retried by a client doesn't cause duplicate processing
- `transactionId` ensures the same business transaction arriving via different channels doesn't duplicate
- ConcurrentHashMap provides thread-safe operations for concurrent processing

### 2.3 Idempotency Flow

```
Incoming Request
       │
       ▼
Check requestId in processedRequests
       │
       ├─ Found → Return DUPLICATE status
       │
       └─ Not Found → Continue
              │
              ▼
Check transactionId in processedTransactions
       │
       ├─ Found → Return DUPLICATE status
       │
       └─ Not Found → Process transaction
              │
              ▼
Store in both maps after successful processing
```

### 2.4 Production Considerations

For production deployment, the in-memory store should be replaced with:
- **Database**: PostgreSQL/MySQL with unique constraints on requestId and transactionId
- **Distributed Cache**: Redis with TTL for request deduplication
- **Dedicated Idempotency Table**: Schema with indexes for fast lookups

---

## 3. Duplicate Detection Logic

### 3.1 Detection Strategy

The service uses **exact matching** for duplicate detection:

1. **Request Duplicates**: Same `requestId` → exact duplicate of the same request
2. **Transaction Duplicates**: Same `transactionId` → same business transaction, possibly different request

### 3.2 Duplicate Handling

When a duplicate is detected:
- The transaction is marked with status `DUPLICATE`
- No business logic is executed
- The duplicate is still stored in the idempotency store for audit trail
- Processing continues with the next transaction (fail-fast isolation)

### 3.3 Edge Cases Handled

- **Late-arriving duplicates**: Detected even if they arrive after the original is processed
- **Same transaction, different requestId**: Detected via transactionId check
- **Same requestId, different transaction**: Treated as error (requestId should be unique per request)

---

## 4. Out-of-Order Handling Strategy

### 4.1 Chosen Approach: Sequence-Based Processing

**Strategy**: Transactions are processed in strict sequence order per account.

### 4.2 Implementation Details

```java
// Track next expected sequence per account
private final Map<String, Long> nextExpectedSequence;

// Buffer pending transactions
private final Map<String, Transaction> pendingTransactions;
```

**Logic**:
1. Each account has a `nextExpectedSequence` counter (starts at 1)
2. Incoming transaction with sequence `N`:
   - If `N == nextExpectedSequence`: Process immediately
   - If `N < nextExpectedSequence`: Mark as DUPLICATE (late arrival)
   - If `N > nextExpectedSequence`: Mark as PENDING, buffer it
3. After processing sequence `N`, increment counter and check if pending transactions can now be processed

### 4.3 Trade-offs

**Pros**:
- Strict ordering guarantees
- Easy to reason about and debug
- Clear audit trail of sequence gaps
- Prevents balance inconsistencies from out-of-order application

**Cons**:
- Can block processing if a transaction is permanently lost
- Requires buffer management for pending transactions
- May increase latency for future transactions if gaps exist

**Production Hardening**:
- Add timeout mechanism: Force process pending transactions after X minutes
- Implement gap detection and alerting
- Consider eventual consistency if strict ordering is not business-critical

### 4.4 Alternative Approaches Considered

- **State-Based Processing**: Valid state transitions only
  - *Rejected*: More complex, harder to validate correctness
- **Deterministic Latest-State**: Use eventTime to determine latest state
  - *Rejected*: For financial transactions, order matters more than latest state

---

## 5. Retry and Failure Handling

### 5.1 Failure Classification

The service distinguishes between two failure types:

1. **Transient Failures**: Temporary issues that may resolve on retry
   - Network timeouts
   - Database connection issues
   - Temporary service unavailability

2. **Permanent Failures**: Business logic failures that won't resolve on retry
   - Insufficient funds
   - Invalid account
   - Validation errors

### 5.2 Retry Mechanism

**Configuration**:
- Maximum retry attempts: 2
- Retry delay: 100ms (simulated)
- Retry status: `RETRY_PENDING`

**Flow**:
```
Transaction Processing
       │
       ▼
Execute Business Logic
       │
       ├─ Success → Mark PROCESSED
       │
       ├─ Permanent Failure → Mark FAILED
       │
       └─ Transient Failure
              │
              ├─ Retry count < MAX → Mark RETRY_PENDING, retry
              │
              └─ Retry count >= MAX → Mark FAILED
```

### 5.3 Simulated Failure Scenario

For assessment purposes, transaction `TXN-2005` simulates a transient failure:
- First attempt: Throws `TransientFailureException`
- Second attempt: Succeeds
- Result: Transaction processed successfully after 1 retry

### 5.4 Retry Safety

Retries are idempotent-safe because:
- Idempotency check happens before retry execution
- Same requestId/transactionId prevents duplicate processing
- State updates are atomic (in-memory maps are thread-safe)

### 5.5 Production Enhancements

- **Exponential backoff**: Increase delay between retries
- **Dead letter queue**: Route permanently failed transactions for manual review
- **Circuit breaker**: Stop retrying if downstream service is down
- **Retry metrics**: Track retry success/failure rates

---

## 6. Observability Approach

### 6.1 Logging Strategy

The service uses **SLF4J with Logback** for structured logging:

**Log Levels**:
- **INFO**: Key processing events, summaries
- **DEBUG**: Detailed state transitions, validation results
- **WARN**: Validation failures, duplicate detection
- **ERROR**: Unexpected errors, processing failures

**Log Format**:
```
HH:mm:ss.SSS [thread] LEVEL logger - message
```

### 6.2 Processing Summary

The `ProcessingResult` class captures:
- Total processed count
- Success count
- Duplicate count
- Failed count
- Pending count
- Status breakdown by type
- Total processing time

### 6.3 Operational Visibility

**Console Output**:
```
=== Processing Summary ===
Total Processed: 6
Successful: 3
Duplicates: 1
Failed: 1
Pending: 1
Processing Time: 150 ms
========================

=== Account Balances ===
Account ACC-201: 85.0
Account ACC-203: 500.0
========================

=== Transaction Details ===
TXN: TXN-2001 | Account: ACC-201 | Type: CREDIT | Amount: 100.0 | Status: PROCESSED | Retries: 0
...
========================
```

### 6.4 Production Enhancements

- **Metrics**: Prometheus/Grafana for processing rates, error rates, latency
- **Structured Logging**: JSON format for log aggregation (ELK, Splunk)
- **Distributed Tracing**: OpenTelemetry for request flow tracking
- **Alerting**: Alert on high failure rates, pending transaction buildup

---

## 7. Data Consistency

### 7.1 Current Implementation

The service uses **in-memory stores** with ConcurrentHashMap for thread-safe operations:
- All state updates are atomic at the map level
- No distributed consistency concerns in single-JVM deployment

### 7.2 Transaction Boundaries

In the current implementation:
- Each transaction is processed independently
- No multi-transaction atomic operations
- Failed transactions don't affect others (isolation)

### 7.3 Production Considerations

For production deployment:
- **Database Transactions**: Use ACID transactions for balance updates
- **Two-Phase Commit**: For distributed operations across multiple services
- **Saga Pattern**: For long-running business processes
- **Compensating Transactions**: For rollback capability

### 7.4 Consistency Guarantees

**Current**: Eventual consistency within single JVM
**Production Target**: Strong consistency with database transactions

---

## 8. Security and Data Handling

### 8.1 Current Security Measures

1. **Input Validation**: All mandatory fields validated before processing
2. **No Hardcoded Secrets**: No API keys or credentials in code
3. **Safe Error Handling**: Exceptions caught and logged without exposing internals
4. **No Sensitive Logging**: Account balances and transaction details logged at INFO level

### 8.2 Data Privacy

- No PII (Personally Identifiable Information) in the model
- Account IDs are abstract references
- No customer names or contact information stored

### 8.3 Production Enhancements

- **Encryption**: Encrypt sensitive data at rest
- **Authentication**: OAuth2/JWT for API access
- **Authorization**: Role-based access control
- **Audit Logging**: Immutable audit trail for all transactions
- **PII Redaction**: Mask sensitive data in logs

---

## 9. AI-Assisted Development Usage

### 9.1 How AI Was Used

AI assistance was leveraged for:
1. **Code Generation**: Initial boilerplate for models, validators, stores
2. **Test Generation**: Unit test templates and edge case scenarios
3. **Documentation**: Structure and formatting of design documentation
4. **Code Review**: Identifying potential improvements and edge cases

### 9.2 Validation of Generated Code

All AI-generated code was:
- **Reviewed for correctness**: Logic verified against requirements
- **Tested manually**: Main application executed to verify behavior
- **Refactored**: Improved for readability and maintainability
- **Customized**: Adapted to specific assessment requirements

### 9.3 AI Limitations Addressed

- **Business logic**: Implemented manually based on domain understanding
- **Edge cases**: Added specific handling for assessment scenarios
- **Production awareness**: Added comments and documentation for real-world deployment
- **Trade-off analysis**: Manually evaluated and documented design choices

---

## 10. Production Hardening Recommendations

### 10.1 Persistence Layer

**Current**: In-memory ConcurrentHashMap
**Production**: 
- PostgreSQL/MySQL for transactional data
- Redis for idempotency cache with TTL
- Connection pooling (HikariCP)

### 10.2 Concurrency

**Current**: Single-threaded batch processing
**Production**:
- Thread pool for parallel transaction processing
- Distributed locks for account-level operations
- Optimistic concurrency control with versioning

### 10.3 Scalability

**Current**: Single JVM, in-memory state
**Production**:
- Horizontal scaling with load balancer
- Partitioning by account ID for data locality
- Message queue (Kafka/RabbitMQ) for async processing

### 10.4 Resilience

**Current**: Basic retry logic
**Production**:
- Circuit breakers (Hystrix/Resilience4j)
- Dead letter queues for failed messages
- Health checks and readiness probes
- Graceful degradation

### 10.5 Monitoring

**Current**: Console logging
**Production**:
- Metrics collection (Prometheus)
- Distributed tracing (Jaeger/Zipkin)
- Log aggregation (ELK stack)
- Alerting (PagerDuty)

### 10.6 Deployment

**Current**: Manual JAR execution
**Production**:
- Containerization (Docker)
- Orchestration (Kubernetes)
- CI/CD pipeline (GitHub Actions/Jenkins)
- Blue-green deployments

### 10.7 Testing

**Current**: Unit tests
**Production**:
- Integration tests with test database
- Contract tests for API boundaries
- Load testing (JMeter/Gatling)
- Chaos engineering for resilience validation

---

## 11. Assumptions and Limitations

### 11.1 Assumptions

1. Single JVM deployment (no distributed coordination needed)
2. Low transaction volume (in-memory state is sufficient)
3. Account IDs are valid references (no account validation service)
4. Currency codes are valid (no currency conversion)
5. No concurrent access to same account in single batch

### 11.2 Limitations

1. **State Loss**: In-memory state lost on JVM restart
2. **No Persistence**: No database integration
3. **Single Region**: No multi-region deployment
4. **No Authentication**: No security layer
5. **No Rate Limiting**: No protection against abuse
6. **No Backpressure**: No flow control for high load

### 11.3 Known Edge Cases

1. **Sequence Gaps**: If a transaction is permanently lost, pending transactions may never process
2. **Clock Skew**: EventTime not used for ordering (sequence-based only)
3. **Large Batches**: No pagination or streaming for large input sets
4. **Memory Limits**: No bounds on in-memory store sizes

---

## 12. Next Steps for Production Readiness

### 12.1 Immediate (P0)

1. Replace in-memory stores with database
2. Add authentication and authorization
3. Implement proper error handling and recovery
4. Add integration tests

### 12.2 Short-term (P1)

1. Add distributed tracing
2. Implement circuit breakers
3. Add metrics and monitoring
4. Containerize the application

### 12.3 Long-term (P2)

1. Multi-region deployment
2. Advanced observability
3. Automated scaling
4. Chaos testing

---

## 13. Conclusion

This implementation demonstrates:
- **Strong core engineering**: Clean architecture, separation of concerns
- **Idempotency**: Dual-level deduplication with clear design
- **Out-of-order handling**: Sequence-based strategy with trade-off analysis
- **Resilience**: Retry logic with transient/permanent failure distinction
- **Observability**: Structured logging and processing summaries
- **Production awareness**: Clear documentation of hardening needs

The service successfully processes the assessment test scenarios:
- Duplicate requests are detected and rejected
- Out-of-order transactions are handled via sequence buffering
- Validation failures are isolated
- Retry scenarios succeed after transient failures
- Processing results are clearly reported

The design is production-aware with clear paths to hardening for real-world deployment.
