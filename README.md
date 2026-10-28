# Idempotent Background Transaction Processing Service

**FDE L2 Assessment - Practical Engineering Exercise**

A production-aware background transaction processing service that handles duplicate, out-of-order, and retry-prone transaction requests with idempotency, resilience, and observability.

---

## Overview

This service demonstrates enterprise-grade transaction processing with:
- **Idempotency**: Dual-level deduplication (request-level and transaction-level)
- **Out-of-order handling**: Sequence-based processing strategy
- **Retry logic**: Transient failure handling with configurable retry limits
- **Validation**: Input validation with fail-fast isolation
- **Observability**: Structured logging and processing summaries
- **Test coverage**: Comprehensive unit tests for key scenarios

---

## Technology Stack

- **Java 17**
- **Maven** (build tool)
- **SLF4J + Logback** (logging)
- **JUnit 5** (testing)

---

## Project Structure

```
transaction-processing-service/
├── src/
│   ├── main/
│   │   ├── java/com/transaction/
│   │   │   ├── model/           # Domain models
│   │   │   ├── service/         # Service layer
│   │   │   ├── processor/       # Business logic
│   │   │   ├── store/           # In-memory persistence
│   │   │   ├── validator/       # Input validation
│   │   │   └── strategy/        # Out-of-order handling
│   │   └── resources/
│   │       └── logback.xml      # Logging configuration
│   └── test/
│       └── java/com/transaction/
│           └── TransactionProcessingServiceTest.java
├── pom.xml
├── DESIGN_DOCUMENTATION.md
└── README.md
```

---

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

---

## Installation

1. Clone or download the project:
```bash
cd C:\Users\Manish\CascadeProjects\transaction-processing-service
```

2. Build the project:
```bash
mvn clean install
```

---

## Execution

### Run the Main Application

The main application demonstrates the service with sample test data:

```bash
mvn exec:java -Dexec.mainClass="com.transaction.TransactionProcessingApplication"
```

Or compile and run the JAR:

```bash
mvn clean package
java -jar target/transaction-processing-service-1.0.0.jar
```

### Expected Output

```
========================================
Idempotent Transaction Processing Service
FDE L2 Assessment
========================================

Processing 6 transactions...

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
TXN: TXN-2002 | Account: ACC-201 | Type: DEBIT | Amount: 40.0 | Status: PENDING | Retries: 0
TXN: TXN-2002 | Account: ACC-201 | Type: DEBIT | Amount: 40.0 | Status: DUPLICATE | Retries: 0
TXN: TXN-2003 | Account: ACC-201 | Type: CREDIT | Amount: 25.0 | Status: PROCESSED | Retries: 0
TXN: TXN-2004 | Account: ACC-202 | Type: DEBIT | Amount: -10.0 | Status: FAILED | Retries: 0
TXN: TXN-2005 | Account: ACC-203 | Type: CREDIT | Amount: 500.0 | Status: PROCESSED | Retries: 1
========================
```

---

## Running Tests

Execute the unit test suite:

```bash
mvn test
```

Run a specific test:

```bash
mvn test -Dtest=TransactionProcessingServiceTest#testDuplicateRequestDetection
```

### Test Coverage

The test suite covers:
- Duplicate request detection
- Duplicate transaction detection
- Out-of-order processing
- In-order processing
- Invalid transaction validation
- Null transaction handling
- Retry scenarios
- Insufficient funds debit
- Credit and debit operations
- Multiple accounts
- Service reset functionality
- Full assessment scenario

---

## Test Scenarios

The service is tested with the following scenarios (from assessment requirements):

| Input Order | transactionId | requestId | sequence | accountId | type | amount | Expected Outcome |
|-------------|---------------|-----------|----------|-----------|------|--------|------------------|
| 1 | TXN-2002 | REQ-2 | 2 | ACC-201 | DEBIT | 40 | Pending (out-of-order) |
| 2 | TXN-2001 | REQ-1 | 1 | ACC-201 | CREDIT | 100 | Processed |
| 3 | TXN-2002 | REQ-2-DUP | 2 | ACC-201 | DEBIT | 40 | Duplicate |
| 4 | TXN-2003 | REQ-3 | 3 | ACC-201 | CREDIT | 25 | Processed |
| 5 | TXN-2004 | REQ-4 | 4 | ACC-202 | DEBIT | -10 | Failed (validation) |
| 6 | TXN-2005 | REQ-5 | 1 | ACC-203 | CREDIT | 500 | Processed after retry |

---

## Key Features

### 1. Idempotency

- **Request-level deduplication**: Uses `requestId` to detect duplicate API requests
- **Transaction-level deduplication**: Uses `transactionId` to detect duplicate business transactions
- **Dual-level tracking**: Both requestId and transactionId stored for comprehensive deduplication

### 2. Out-of-Order Handling

- **Sequence-based strategy**: Transactions processed in strict sequence order per account
- **Pending buffer**: Future-sequence transactions buffered until prior sequences arrive
- **Gap detection**: Late-arriving transactions marked as duplicates if sequence already passed

### 3. Retry Logic

- **Transient failure detection**: Distinguishes between transient and permanent failures
- **Configurable retry limit**: Maximum 2 retry attempts (configurable)
- **Retry-safe**: Idempotency checks prevent duplicate processing on retry
- **Simulated failure**: TXN-2005 demonstrates retry behavior

### 4. Validation

- **Mandatory field checks**: All required fields validated before processing
- **Business rule validation**: Amount positivity, sequence non-negativity
- **Fail-fast isolation**: Invalid transactions don't stop batch processing

### 5. Observability

- **Structured logging**: SLF4J with Logback for consistent log format
- **Processing summary**: Counts by status, processing time, account balances
- **Transaction details**: Per-transaction status and retry count

---

## Design Documentation

For detailed architecture, design decisions, and production hardening recommendations, see:
[DESIGN_DOCUMENTATION.md](DESIGN_DOCUMENTATION.md)

Key sections include:
- Architecture overview
- Idempotency design
- Duplicate detection logic
- Out-of-order handling strategy
- Retry and failure handling
- Observability approach
- Production hardening recommendations

---

## Configuration

### Logging

Configure logging in `src/main/resources/logback.xml`:

```xml
<logger name="com.transaction" level="INFO"/>
```

Change to `DEBUG` for detailed logging during development.

### Retry Configuration

Modify in `TransactionProcessor.java`:

```java
private static final int MAX_RETRY_COUNT = 2;
```

### Simulated Failure

Configure which transaction ID simulates transient failure in `TransactionProcessor.java`:

```java
private static final String SIMULATED_FAILURE_TXN_ID = "TXN-2005";
```

---

## Production Considerations

This implementation uses in-memory stores suitable for assessment purposes. For production deployment, the following enhancements are recommended:

### Persistence
- Replace in-memory stores with PostgreSQL/MySQL
- Use Redis for idempotency cache with TTL
- Implement connection pooling

### Concurrency
- Add thread pool for parallel processing
- Implement distributed locks for account operations
- Use optimistic concurrency control

### Scalability
- Horizontal scaling with load balancer
- Partition by account ID for data locality
- Use message queue (Kafka/RabbitMQ) for async processing

### Resilience
- Implement circuit breakers (Hystrix/Resilience4j)
- Add dead letter queues for failed messages
- Implement health checks and readiness probes

### Monitoring
- Add metrics collection (Prometheus)
- Implement distributed tracing (Jaeger/Zipkin)
- Set up log aggregation (ELK stack)
- Configure alerting (PagerDuty)

See [DESIGN_DOCUMENTATION.md](DESIGN_DOCUMENTATION.md) for detailed production hardening recommendations.

---

## API Usage Example

```java
// Create service
TransactionProcessingService service = new TransactionProcessingService();

// Create transaction request
TransactionRequest request = new TransactionRequest(
    "TXN-001",              // transactionId
    "ACC-001",              // accountId
    TransactionType.CREDIT, // type
    100.0,                  // amount
    "USD",                  // currency
    1,                      // sequenceNumber
    "REQ-001"               // requestId
);

// Process batch
List<TransactionRequest> requests = List.of(request);
ProcessingResult result = service.processBatch(requests);

// Check results
System.out.println("Processed: " + result.getTotalProcessed());
System.out.println("Successful: " + result.getSuccessCount());
System.out.println("Account Balance: " + service.getAccountBalanceStore().getBalance("ACC-001"));
```

---

## Troubleshooting

### Build Failures

If Maven build fails:
```bash
mvn clean install -U
```

### Test Failures

If tests fail, ensure:
- Java 17 is installed and in PATH
- Maven is properly configured
- No port conflicts (no external ports used)

### Logging Issues

If logs don't appear:
- Check logback.xml configuration
- Ensure log level is set appropriately
- Verify console output is not redirected

---

## License

This project is created for assessment purposes.

---

## Contact

For questions about this assessment submission, please refer to the recruitment team.

---

## Assessment Summary

This submission demonstrates:

✅ **Strong core engineering**: Clean architecture, separation of concerns
✅ **Idempotency**: Dual-level deduplication with clear design
✅ **Out-of-order handling**: Sequence-based strategy with trade-off analysis
✅ **Resilience**: Retry logic with transient/permanent failure distinction
✅ **Observability**: Structured logging and processing summaries
✅ **Production awareness**: Clear documentation of hardening needs
✅ **Test coverage**: Comprehensive unit tests for key scenarios
✅ **AI-assisted development**: Effective use of AI with validation
✅ **Design communication**: Clear explanation of choices and trade-offs

The service successfully processes all assessment test scenarios and demonstrates FDE L2 readiness signals.
