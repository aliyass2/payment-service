# Payment Service

A RESTful payment processing service built with Spring Boot 3.5.11 and Java 21.

## Tech Stack

- **Java 21** / **Spring Boot 3.5.11**
- **Spring Data JPA** — data persistence
- **H2** — in-memory database (development & testing)
- **Lombok** — boilerplate reduction
- **Maven** — build tool

## Getting Started

**Prerequisites:** Java 21+

```bash
# Clone and run
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

## API Endpoints

All endpoints are prefixed with `/api/payments`.

| Method | Path              | Description           | Status |
|--------|-------------------|-----------------------|--------|
| GET    | `/api/payments`   | List all payments     | 200    |
| GET    | `/api/payments/{id}` | Get payment by ID  | 200    |
| POST   | `/api/payments`   | Create a new payment  | 201    |
| PUT    | `/api/payments/{id}` | Update a payment   | 200    |
| DELETE | `/api/payments/{id}` | Delete a payment   | 204    |

### Payment Object

```json
{
  "id": 1,
  "amount": 99.99,
  "status": "PENDING",
  "createdAt": "2026-03-18T10:00:00"
}
```

`createdAt` is set automatically on creation. `amount` and `status` are required.

## Architecture

```
controller/  →  PaymentController   (REST, /api/payments)
service/     →  PaymentService      (business logic)
repository/  →  PaymentRepository   (JpaRepository<Payment, Long>)
model/       →  Payment             (JPA entity, table: payments)
```

## Development

```bash
# Build
./mvnw clean install

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Package executable JAR
./mvnw clean package
java -jar target/payment-service-0.0.1-SNAPSHOT.jar
```

> On Windows use `mvnw.cmd` instead of `./mvnw`.

## Configuration

Application config is in `src/main/resources/application.yaml`. Copy `.env.example` to `.env` for any local environment overrides (never commit `.env`).
