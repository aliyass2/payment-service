# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean install

# Run
./mvnw spring-boot:run

# Test (all)
./mvnw test

# Test (single class)
./mvnw test -Dtest=ClassName

# Test (single method)
./mvnw test -Dtest=ClassName#methodName

# Package JAR
./mvnw clean package
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Architecture

Spring Boot 3.5.11 / Java 21 REST service under `com.scopesky.paymentservice`.

**Key dependencies:**
- `spring-boot-starter-web` — REST controllers
- `spring-boot-starter-data-jpa` — JPA repositories
- `spring-boot-starter-validation` — Bean validation (`@Valid`, constraint annotations)
- `h2` (runtime) — In-memory database (dev/test)
- `lombok` — Boilerplate reduction (`@Data`, `@Builder`, etc.)

**Standard layering:** Controllers → Services → Repositories (JPA) → H2 database.

Config lives in `src/main/resources/application.yaml`. Test config overrides in `src/test/resources/application.yaml`.
