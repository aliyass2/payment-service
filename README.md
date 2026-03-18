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
