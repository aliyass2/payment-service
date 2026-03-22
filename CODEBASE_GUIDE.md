# Payment Service — Codebase Guide
> Written for a .NET developer transitioning to Spring Boot / Java

---

## Table of Contents

1. [The Big Picture — .NET vs Spring Boot](#1-the-big-picture--net-vs-spring-boot)
2. [Project Structure](#2-project-structure)
3. [The Domain Model](#3-the-domain-model)
4. [Annotations — The Rosetta Stone](#4-annotations--the-rosetta-stone)
5. [Layer 1 — Entities (Model)](#5-layer-1--entities-model)
6. [Layer 2 — Repositories (Data Access)](#6-layer-2--repositories-data-access)
7. [Layer 3 — DTOs](#7-layer-3--dtos)
8. [Layer 4 — Services (Business Logic)](#8-layer-4--services-business-logic)
9. [Layer 5 — Controllers (API)](#9-layer-5--controllers-api)
10. [Exception Handling](#10-exception-handling)
11. [Configuration (application.yaml)](#11-configuration-applicationyaml)
12. [Testing](#12-testing)
13. [Request Lifecycle — End to End](#13-request-lifecycle--end-to-end)

---

## 1. The Big Picture — .NET vs Spring Boot

If you've built REST APIs in ASP.NET Core, the Spring Boot mental model maps almost 1-to-1. The vocabulary is different, the wiring is different, but the architecture is identical.

| ASP.NET Core concept | Spring Boot equivalent |
|---|---|
| `Program.cs` + `Startup.cs` | `@SpringBootApplication` main class |
| `IServiceCollection.AddScoped<I, T>()` | `@Service` / `@Component` (auto-detected) |
| `IServiceCollection.AddDbContext<T>()` | Auto-configured by `spring-boot-starter-data-jpa` |
| `DbContext` | `EntityManager` (managed by JPA/Hibernate) |
| `DbSet<T>` | `JpaRepository<T, ID>` |
| `[ApiController] [Route("api/...")]` | `@RestController @RequestMapping("/api/...")` |
| `[HttpGet] [HttpPost]` etc. | `@GetMapping` `@PostMapping` etc. |
| `[FromBody]` | `@RequestBody` |
| `[FromRoute]` | `@PathVariable` |
| `[FromQuery]` | `@RequestParam` |
| `IActionResult` / `ActionResult<T>` | `ResponseEntity<T>` |
| Data Annotations (`[Required]`, `[EmailAddress]`) | Jakarta Validation (`@NotBlank`, `@Email`) |
| `[ApiController]` auto-400 on validation failure | `@Valid` triggers validation |
| `UseExceptionHandler` middleware | `@RestControllerAdvice` |
| `appsettings.json` | `application.yaml` |
| `xUnit` / `Moq` | `JUnit 5` / `Mockito` |
| `WebApplicationFactory<T>` integration tests | `@SpringBootTest` + `MockMvc` |
| `dotnet run` | `./mvnw spring-boot:run` |
| `dotnet test` | `./mvnw test` |

### The biggest conceptual shift: Dependency Injection

In ASP.NET Core you **explicitly register** every service:
```csharp
// .NET — you must do this for each dependency
builder.Services.AddScoped<IUserService, UserService>();
builder.Services.AddScoped<IWalletService, WalletService>();
```

In Spring Boot you **annotate** a class and it's automatically discovered and registered:
```java
@Service   // ← this is the entire registration
public class UserService { ... }
```

Spring scans the classpath for `@Service`, `@Repository`, `@Controller`, `@Component` and registers them all automatically. You never write a "startup registration" block.

Constructor injection (the preferred style, same as .NET) works like this:
```java
@Service
@RequiredArgsConstructor   // ← Lombok generates the constructor for you
public class UserService {
    private final UserRepository userRepository;  // injected automatically
    private final WalletRepository walletRepository;
}
```
`@RequiredArgsConstructor` is Lombok (see Section 4) generating the `public UserService(UserRepository r, WalletRepository w)` constructor, which Spring then uses for injection. This is equivalent to .NET's constructor injection pattern.

---

## 2. Project Structure

```
src/
├── main/
│   ├── java/com/scopesky/paymentservice/
│   │   ├── PaymentServiceApplication.java   ← entry point (like Program.cs)
│   │   ├── model/                           ← entities + enums (like EF Core models)
│   │   │   ├── enums/
│   │   │   ├── User.java
│   │   │   ├── Wallet.java
│   │   │   ├── Transaction.java
│   │   │   ├── Transfer.java
│   │   │   └── PaymentMethod.java
│   │   ├── repository/                      ← data access (like DbContext/IRepository)
│   │   ├── dto/                             ← request/response shapes (like ViewModels/DTOs)
│   │   │   ├── user/
│   │   │   ├── wallet/
│   │   │   ├── transaction/
│   │   │   ├── transfer/
│   │   │   └── paymentmethod/
│   │   ├── service/                         ← business logic (like your service layer)
│   │   ├── controller/                      ← HTTP endpoints (like Controllers)
│   │   └── exception/                       ← custom exceptions + global handler
│   └── resources/
│       └── application.yaml                 ← appsettings equivalent
└── test/
    └── java/com/scopesky/paymentservice/
        ├── BaseIntegrationTest.java          ← shared test base (like a TestBase class)
        ├── controller/                       ← integration tests
        └── service/                          ← unit tests
```

The `pom.xml` in the root is the equivalent of your `.csproj` file — it lists dependencies and build settings.

---

## 3. The Domain Model

Before diving into code, understand what the system models:

```
User
 └── has many Wallets
      └── has many Transactions  ← immutable ledger entries
 └── has many PaymentMethods

Transfer
 ├── links sourceWallet → destinationWallet
 ├── links debitTransaction (TRANSFER_OUT on source)
 └── links creditTransaction (TRANSFER_IN on destination)
```

**Money flow rules:**
- A **Wallet** holds a balance in a single currency
- Every time money moves, a **Transaction** is written — and never changed
- A **Transfer** is a coordination record linking two Transactions (one debit, one credit)
- Refunds and reversals create *new* Transaction records — they never edit the original
- This is the **immutable ledger** pattern used in real payment systems

### Enums

Enums in Java are full classes. Ours are simple value lists:

```java
// model/enums/TransactionType.java
public enum TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT, PAYMENT, REFUND, REVERSAL
}
```

In C# you'd write `public enum TransactionType { Deposit, Withdrawal, ... }`. Same concept, different casing convention (Java uses UPPER_SNAKE_CASE for enum values).

---

## 4. Annotations — The Rosetta Stone

Annotations are Java's equivalent of C# attributes. They sit on classes, methods, and fields and tell frameworks how to treat them. Here are every annotation used in this project:

### Lombok annotations (code generation — like C# source generators)

Lombok reads these at compile time and generates boilerplate code you'd otherwise write by hand.

| Annotation | What it generates | C# equivalent |
|---|---|---|
| `@Data` | Getters, setters, `equals()`, `hashCode()`, `toString()` | `record` or auto-properties |
| `@Builder` | A fluent builder: `User.builder().email("...").build()` | Object initializer `new User { Email = "..." }` |
| `@NoArgsConstructor` | `public User() {}` | Default constructor |
| `@AllArgsConstructor` | Constructor with all fields as parameters | All-args constructor |
| `@RequiredArgsConstructor` | Constructor for all `final` fields only | Constructor injection |

`@Builder.Default` on a field sets the default value when using the builder:
```java
@Builder.Default
private BigDecimal balance = BigDecimal.ZERO;
// Without this, balance would be null when built
```

### JPA / Hibernate annotations (like Entity Framework Data Annotations)

| Annotation | Meaning | EF Core equivalent |
|---|---|---|
| `@Entity` | This class maps to a database table | `[Table]` or just inheriting `DbContext` config |
| `@Table(name="users")` | Override the table name | `[Table("users")]` |
| `@Id` | This is the primary key | `[Key]` |
| `@GeneratedValue(strategy = IDENTITY)` | Auto-increment (IDENTITY column) | `ValueGeneratedOnAdd()` |
| `@Column(nullable=false)` | NOT NULL constraint | `[Required]` |
| `@Column(unique=true)` | UNIQUE constraint | `HasIndex(...).IsUnique()` |
| `@Column(updatable=false)` | Read-only after insert | No direct equivalent — manually enforced |
| `@Enumerated(EnumType.STRING)` | Store enum as VARCHAR, not integer | `.HasConversion<string>()` |
| `@ManyToOne` | Many-to-one relationship (FK side) | `[ForeignKey]` navigation property |
| `@OneToMany(mappedBy=...)` | One-to-many (inverse side, no FK column) | `ICollection<T>` navigation |
| `@OneToOne` | One-to-one relationship | Navigation property |
| `@JoinColumn(name="user_id")` | Name of the FK column in the database | `.HasForeignKey(x => x.UserId)` |
| `@PrePersist` | Runs before INSERT — like a lifecycle hook | `SaveChangesAsync` override |
| `@PreUpdate` | Runs before UPDATE | `SaveChangesAsync` override |

### Spring Web annotations

| Annotation | Meaning | ASP.NET Core equivalent |
|---|---|---|
| `@RestController` | HTTP controller, all responses serialised to JSON | `[ApiController]` |
| `@RequestMapping("/api/users")` | Base URL for all methods in this controller | `[Route("api/users")]` |
| `@GetMapping("/{id}")` | HTTP GET, path `/api/users/{id}` | `[HttpGet("{id}")]` |
| `@PostMapping` | HTTP POST | `[HttpPost]` |
| `@PutMapping` | HTTP PUT | `[HttpPut]` |
| `@DeleteMapping` | HTTP DELETE | `[HttpDelete]` |
| `@PathVariable` | Extract `{id}` from URL | `[FromRoute]` |
| `@RequestBody` | Deserialise JSON body | `[FromBody]` |
| `@RequestParam` | Query string parameter | `[FromQuery]` |
| `@Valid` | Run Jakarta Validation on this argument | `[ApiController]` triggers this automatically |

### Spring core annotations

| Annotation | Meaning |
|---|---|
| `@Service` | Marks class as a service bean (business logic layer) |
| `@Repository` | Marks class as a repository bean (data layer) |
| `@RestControllerAdvice` | Global exception handler — intercepts exceptions from all controllers |
| `@ExceptionHandler(Foo.class)` | This method handles exceptions of type `Foo` |
| `@Transactional` | Wrap this method in a database transaction; auto-rollback on exception |
| `@SpringBootApplication` | Entry point annotation — enables component scanning and auto-config |
| `@SpringBootTest` | Integration test — boots the full Spring context |
| `@AutoConfigureMockMvc` | Wires up MockMvc in a `@SpringBootTest` |
| `@Transactional` (on test) | Rolls back the database after every test |
| `@ExtendWith(MockitoExtension.class)` | Enables Mockito in a unit test (equivalent of `[Fact]` with Moq setup) |
| `@Mock` | Creates a mock of this type (like `new Mock<T>()` in Moq) |
| `@InjectMocks` | Creates the class under test and injects `@Mock` fields into it |
| `@BeforeEach` | Runs before each test (like `[SetUp]` in NUnit or constructor in xUnit) |

---

## 5. Layer 1 — Entities (Model)

Entities are classes that map directly to database tables. Hibernate (the JPA provider, equivalent to EF Core) reads the annotations and generates the SQL schema.

### User.java — annotated walkthrough

```java
@Entity                          // "this class is a database table"
@Table(name = "users")           // table name override (default would be "user", a reserved word)
@Data                            // Lombok: generates getters/setters/equals/hashCode
@Builder                         // Lombok: enables User.builder().email("...").build()
@NoArgsConstructor               // Lombok: required by JPA (JPA needs a no-arg constructor)
@AllArgsConstructor              // Lombok: needed by @Builder when combined with @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto-increment primary key
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String referenceId;  // business-facing ID (USR-XXXXXXXX), never changes after insert

    @Column(nullable = false)
    private String firstName;

    @Column(unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)  // stores "ACTIVE" not 0 in the DB
    @Column(nullable = false)
    private UserStatus status;

    // Relationship: one User owns many Wallets
    // "mappedBy = user" means the FK lives in the Wallet table (wallet.user_id), not here
    // CascadeType.ALL: if you delete a User, their Wallets are deleted too
    // FetchType.LAZY: don't load wallets until you explicitly call user.getWallets()
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Wallet> wallets = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // JPA lifecycle hook — called automatically before INSERT
    @PrePersist
    protected void onCreate() {
        referenceId = "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**Key difference from EF Core:** In EF Core you configure relationships in `OnModelCreating`. In JPA you configure them directly on the entity fields with annotations. The `mappedBy` attribute tells JPA "the foreign key column lives on the other side of this relationship."

### FetchType.LAZY vs EAGER

This is important to understand:
- `LAZY` (default for collections): the related data is **not** loaded until you access it. Like `AsQueryable()` with deferred loading.
- `EAGER`: related data is always loaded with a JOIN.

We use `LAZY` everywhere to avoid inadvertently loading entire object graphs. When you call `wallet.getUser().getReferenceId()` in a service method, JPA loads the User at that point. This is why services must be `@Transactional` — the database session must be open when the lazy load happens.

### Transaction.java — the ledger entry

```java
@Entity
@Table(name = "transactions")
public class Transaction {

    // ...standard id/referenceId fields...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;              // FK column "wallet_id" in this table

    @Enumerated(EnumType.STRING)
    private TransactionType type;       // DEPOSIT, WITHDRAWAL, PAYMENT, etc.

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;   // COMPLETED, REVERSED, etc.

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;          // precision=19 scale=4 → up to 999,999,999,999,999.9999

    private BigDecimal balanceBefore;   // snapshot of balance at time of transaction
    private BigDecimal balanceAfter;    // snapshot of balance after transaction

    @Column
    private Long relatedTransactionId;  // links REFUND → original PAYMENT, REVERSAL → original

    @Column(unique = true)
    private String idempotencyKey;      // prevents duplicate operations on retry
}
```

**Why `BigDecimal` and not `double`?**
Never use `double` or `float` for money in any language. `0.1 + 0.2` in floating point is `0.30000000000000004`. `BigDecimal` is exact decimal arithmetic — same as `decimal` in C#.

---

## 6. Layer 2 — Repositories (Data Access)

In EF Core you write queries against a `DbSet<T>` inside a `DbContext`. In Spring Data JPA, you declare an **interface** and Spring generates the implementation for you at startup.

```java
// repository/UserRepository.java
public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository<EntityType, PrimaryKeyType>
    // gives you: save(), findById(), findAll(), deleteById(), count(), existsById(), etc.
    // — equivalent to a full IRepository<User> in .NET with all CRUD operations

    Optional<User> findByEmail(String email);
    // Spring reads the method name and generates: SELECT * FROM users WHERE email = ?
    // "find" + "By" + "Email" → WHERE email = :email
    // Returns Optional<User> — Java's equivalent of nullable, forces you to handle the null case

    Optional<User> findByReferenceId(String referenceId);
    // SELECT * FROM users WHERE reference_id = ?

    boolean existsByEmail(String email);
    // SELECT COUNT(*) > 0 FROM users WHERE email = ?
}
```

This method-name query generation is called **Spring Data query derivation**. The naming rules are:
- `findBy` → SELECT WHERE
- `existsBy` → SELECT COUNT > 0
- `deleteBy` → DELETE WHERE
- `findByEmailAndStatus` → WHERE email = ? AND status = ?
- `findByUserIdOrderByCreatedAtDesc` → WHERE user_id = ? ORDER BY created_at DESC

For complex queries you can use `@Query("SELECT u FROM User u WHERE ...")` with JPQL (Java's equivalent of LINQ to Entities).

### TransactionRepository

```java
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    // Used to check if a duplicate request came in before processing

    List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);
    // SELECT * FROM transactions WHERE wallet_id = ? ORDER BY created_at DESC
    // Notice "Id" at the end of "WalletId" — Spring knows wallet_id is the FK column

    List<Transaction> findByWalletIdAndTypeOrderByCreatedAtDesc(Long walletId, TransactionType type);
    // Adds AND type = ? to the above
}
```

**`Optional<T>` vs C# nullable**

`Optional<T>` is Java's way of expressing "this might be null." You use it like this:

```java
// Instead of:
User user = userRepository.findByEmail(email);  // could be null — dangerous
if (user == null) throw new UserNotFoundException(...);

// You write:
User user = userRepository.findByEmail(email)
    .orElseThrow(() -> new UserNotFoundException(email));
// orElseThrow: unwrap the Optional, or throw if empty
// This is similar to C#'s: user ?? throw new UserNotFoundException(email)
```

---

## 7. Layer 3 — DTOs

DTOs (Data Transfer Objects) in Spring Boot work exactly the same as in .NET. Request DTOs come in from HTTP, response DTOs go out. Entities never leave the service layer.

```java
// dto/user/CreateUserRequest.java
@Data                    // Lombok: getters + setters
@NoArgsConstructor       // required for JSON deserialisation
@AllArgsConstructor      // convenient for tests
public class CreateUserRequest {

    @NotBlank(message = "First name is required")  // equivalent to [Required]
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")        // equivalent to [EmailAddress]
    private String email;

    private String phone;  // optional — no validation annotation
}
```

Validation annotations come from `spring-boot-starter-validation` (Jakarta Validation). Common ones:

| Jakarta annotation | .NET equivalent |
|---|---|
| `@NotNull` | `[Required]` (for objects) |
| `@NotBlank` | `[Required]` (for strings — also rejects empty/whitespace) |
| `@Email` | `[EmailAddress]` |
| `@DecimalMin("0.01")` | `[Range(0.01, double.MaxValue)]` |
| `@Size(min=2, max=50)` | `[StringLength(50, MinimumLength=2)]` |
| `@Pattern(regexp="...")` | `[RegularExpression("...")]` |

**Validation is triggered by `@Valid` on the controller parameter.** If validation fails, Spring throws `MethodArgumentNotValidException`, which our `GlobalExceptionHandler` catches and returns a 400 with field-level error messages.

### Response DTOs

```java
// dto/user/UserResponse.java
@Data
@Builder           // enables UserResponse.builder().id(1L).email("...").build()
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String referenceId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

The mapping from entity → DTO is done manually in the service layer (in a private `toResponse()` method). Spring Boot does not have AutoMapper built-in — you either map manually (as we do here) or use a library like MapStruct.

---

## 8. Layer 4 — Services (Business Logic)

Services hold all business logic and are the only layer that knows about both repositories and DTOs. Nothing else should call repositories directly.

### @Transactional

`@Transactional` wraps the method in a database transaction. If any exception is thrown (unchecked — `RuntimeException` or its subclasses), the transaction is rolled back automatically.

```java
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional  // ← BEGIN TRANSACTION; auto-ROLLBACK if exception thrown
    public TransactionResponse deposit(Long walletId, DepositRequest request) {

        // 1. Load or throw
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        // 2. Idempotency check — if this key was used before, reject the duplicate
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new DuplicateIdempotencyKeyException(request.getIdempotencyKey());
                });

        // 3. Guard: wallet must be ACTIVE
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletFrozenException(wallet.getId());
        }

        // 4. Business logic: update balance
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(balanceBefore.add(request.getAmount()));  // immutable BigDecimal — .add() returns new value
        walletRepository.save(wallet);

        // 5. Write immutable ledger entry
        Transaction txn = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        return toTransactionResponse(transactionRepository.save(txn));
        // ← COMMIT (if no exception)
    }
}
```

**`BigDecimal` arithmetic:** Unlike C#'s `decimal` which uses operators (`a + b`), Java's `BigDecimal` uses methods:
- `a.add(b)` → `a + b`
- `a.subtract(b)` → `a - b`
- `a.multiply(b)` → `a * b`
- `a.compareTo(b)` → returns -1, 0, or 1 (use this instead of `<`, `>`)
- `a.compareTo(b) < 0` → `a < b`

### TransferService — atomicity demonstration

The transfer is the most critical operation because it must touch 5 database objects atomically:

```java
@Transactional  // ← ALL of this is one atomic transaction
public TransferResponse transfer(TransferRequest request) {
    // Validate
    if (request.getSourceWalletId().equals(request.getDestinationWalletId())) {
        throw new InvalidOperationException("Cannot transfer to the same wallet");
    }

    // Load both wallets
    Wallet source = walletRepository.findById(request.getSourceWalletId())
            .orElseThrow(() -> new WalletNotFoundException(request.getSourceWalletId()));
    Wallet destination = walletRepository.findById(request.getDestinationWalletId())
            .orElseThrow(() -> new WalletNotFoundException(request.getDestinationWalletId()));

    // Guard checks...
    if (source.getCurrency() != destination.getCurrency()) {
        throw new WalletCurrencyMismatchException(source.getCurrency(), destination.getCurrency());
    }

    // Debit source
    source.setBalance(source.getBalance().subtract(request.getAmount()));
    walletRepository.save(source);                        // save 1

    Transaction debitTxn = transactionRepository.save(   // save 2
        Transaction.builder().wallet(source).type(TransactionType.TRANSFER_OUT)...build());

    // Credit destination
    destination.setBalance(destination.getBalance().add(request.getAmount()));
    walletRepository.save(destination);                   // save 3

    Transaction creditTxn = transactionRepository.save(  // save 4
        Transaction.builder().wallet(destination).type(TransactionType.TRANSFER_IN)...build());

    return toResponse(transferRepository.save(           // save 5
        Transfer.builder()...debitTransaction(debitTxn).creditTransaction(creditTxn)...build()));
    // If ANY of the 5 saves fail, ALL are rolled back
}
```

---

## 9. Layer 5 — Controllers (API)

Controllers in Spring Boot are nearly identical to ASP.NET Core controllers. The main difference is the annotation style.

```java
@RestController             // equivalent to [ApiController] — responses are JSON by default
@RequestMapping("/api/users")  // base route for all methods
@RequiredArgsConstructor    // Lombok: injects UserService via constructor
public class UserController {

    private final UserService userService;

    @PostMapping                    // HTTP POST /api/users
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        //    ^^^^^  ← triggers Jakarta Validation on CreateUserRequest
        //          ^^^^^^^^^^^^ ← reads JSON body into CreateUserRequest

        return ResponseEntity.status(HttpStatus.CREATED)  // 201
                .body(userService.createUser(request));
    }

    @GetMapping("/{id}")             // HTTP GET /api/users/{id}
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id) { // ← extracts {id} from URL
        return ResponseEntity.ok(userService.getUserById(id));  // 200 OK
    }

    @PutMapping("/{id}/suspend")     // HTTP PUT /api/users/{id}/suspend
    public ResponseEntity<UserResponse> suspendUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.suspendUser(id));
    }
}
```

**`ResponseEntity<T>`** is the equivalent of `ActionResult<T>` in ASP.NET Core. It wraps the response body and lets you set the HTTP status code explicitly.

Common response patterns:
```java
ResponseEntity.ok(body)                           // 200 OK
ResponseEntity.status(HttpStatus.CREATED).body(x) // 201 Created
ResponseEntity.noContent().build()                // 204 No Content
ResponseEntity.notFound().build()                 // 404 Not Found (rarely used — exceptions do this)
```

---

## 10. Exception Handling

In ASP.NET Core you write middleware (`UseExceptionHandler`) or use `IExceptionFilter`. In Spring Boot, you write a `@RestControllerAdvice` class.

```java
@RestControllerAdvice  // ← intercepts exceptions from ALL controllers in the application
public class GlobalExceptionHandler {

    // Handle any of these 5 exception types the same way → 404
    @ExceptionHandler({
            UserNotFoundException.class,
            WalletNotFoundException.class,
            TransactionNotFoundException.class,
            TransferNotFoundException.class,
            PaymentMethodNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(InsufficientFundsException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());  // 422
    }

    // 400 for validation failures — triggered by @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Fallback — catches everything else → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
```

All error responses have the same shape:
```json
{
  "status": 404,
  "message": "User not found with id: 42",
  "timestamp": "2026-03-22T14:00:00.123456"
}
```

Custom exceptions are simple `RuntimeException` subclasses — no special interface required:

```java
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
    public UserNotFoundException(String referenceId) {
        super("User not found with referenceId: " + referenceId);
    }
}
```

**Why `RuntimeException` and not `Exception`?**
Java has checked exceptions (`Exception`) and unchecked exceptions (`RuntimeException`). Checked exceptions must be declared in method signatures (`throws IOException`). Spring's `@Transactional` only rolls back on unchecked (`RuntimeException`) by default. All our custom exceptions extend `RuntimeException` so they: (1) automatically trigger transaction rollback, (2) don't pollute method signatures, and (3) propagate naturally up to the `@RestControllerAdvice`.

---

## 11. Configuration (application.yaml)

```yaml
spring:
  application:
    name: payment-service      # app name, used in logs and Spring Cloud

  datasource:
    url: jdbc:h2:mem:paymentdb # H2 in-memory database named "paymentdb"
                               # dies when the app stops — perfect for development
    driver-class-name: org.h2.Driver
    username: sa
    password:                  # empty password

  jpa:
    hibernate:
      ddl-auto: update         # Hibernate auto-creates/updates tables from entity classes
                               # "update" = create if missing, alter if schema changed
                               # "create-drop" (used in tests) = create fresh, drop on shutdown
    show-sql: true             # logs every SQL query to console — useful during development
    properties:
      hibernate:
        format_sql: true       # pretty-prints the SQL (indented)

  h2:
    console:
      enabled: true
      path: /h2-console        # visit http://localhost:8080/h2-console to browse the DB
```

The test config at `src/test/resources/application.yaml` overrides `ddl-auto: create-drop` so each test run gets a clean schema.

**`ddl-auto` options explained:**
| Value | Behaviour |
|---|---|
| `none` | Do nothing — your responsibility to manage schema |
| `validate` | Validate schema matches entities; fail if mismatch |
| `update` | Apply only the diff — add missing columns/tables |
| `create` | DROP + CREATE on startup |
| `create-drop` | CREATE on startup, DROP on shutdown |

In production you'd use `none` or `validate` and manage migrations with Flyway or Liquibase (equivalent to EF Core migrations).

---

## 12. Testing

### Unit Tests — Mockito (equivalent to xUnit + Moq)

```java
@ExtendWith(MockitoExtension.class)  // enables Mockito — like [Fact] with Moq setup
class UserServiceTest {

    @Mock
    private UserRepository userRepository;    // equivalent to new Mock<IUserRepository>()

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks                              // creates UserService(userRepository, walletRepository)
    private UserService userService;          // equivalent to new UserService(userRepo.Object, walletRepo.Object)

    private User activeUser;

    @BeforeEach                               // runs before each test — like [SetUp] in NUnit
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .status(UserStatus.ACTIVE)
                .email("alice@example.com")
                .build();
    }

    @Test
    void createUser_duplicateEmail_throwsInvalidOperationException() {
        // Arrange — equivalent to mock.Setup(r => r.ExistsByEmail("alice@example.com")).Returns(true)
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Act + Assert — equivalent to Assert.Throws<InvalidOperationException>(() => ...)
        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("Alice", "Smith", "alice@example.com", null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already registered");

        // Verify — equivalent to mock.Verify(r => r.Save(It.IsAny<User>()), Times.Never)
        verify(userRepository, never()).save(any());
    }
}
```

**Mockito ↔ Moq cheat sheet:**

| Moq | Mockito |
|---|---|
| `new Mock<IRepo>()` | `@Mock UserRepository userRepository` |
| `mock.Object` | just `userRepository` |
| `mock.Setup(r => r.Find(1L)).Returns(user)` | `when(userRepository.findById(1L)).thenReturn(Optional.of(user))` |
| `mock.Setup(...).Throws(new Exception())` | `when(...).thenThrow(new RuntimeException())` |
| `mock.Verify(r => r.Save(It.IsAny<User>()), Times.Once())` | `verify(userRepository, times(1)).save(any())` |
| `It.IsAny<User>()` | `any(User.class)` or just `any()` |
| `Times.Never()` | `never()` |
| `mock.Setup(r => r.Save(It.IsAny<User>())).Returns(u => u.Parameters[0])` | `when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0))` |

### Integration Tests — MockMvc (equivalent to WebApplicationFactory)

```java
@SpringBootTest        // starts the full Spring application context (like WebApplicationFactory)
@AutoConfigureMockMvc  // wires up MockMvc for HTTP request simulation
@Transactional         // rolls back DB after every test
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;        // the HTTP client — like HttpClient in .NET integration tests

    @Autowired
    protected ObjectMapper objectMapper;  // Jackson JSON serialiser/deserialiser (like System.Text.Json)
}
```

A typical integration test:

```java
@Test
void deposit_validRequest_returns200WithCompletedTransaction() throws Exception {
    // Arrange: create a user and get their default wallet
    long userId = createUserViaApi("Alice", "Smith", "alice@example.com");
    // walletId obtained from the auto-created default wallet...

    // Act: fire an HTTP request
    DepositRequest req = new DepositRequest(new BigDecimal("100.00"), "Initial deposit", "key-dep-1");
    mockMvc.perform(
                post("/api/wallets/" + walletId + "/deposit")  // URL
                .contentType(MediaType.APPLICATION_JSON)       // Content-Type header
                .content(toJson(req))                          // serialised body
            )
            // Assert: chain expectations on the response
            .andExpect(status().isOk())                        // 200
            .andExpect(jsonPath("$.type").value("DEPOSIT"))    // body.type == "DEPOSIT"
            .andExpect(jsonPath("$.balanceBefore").value(0))   // body.balanceBefore == 0
            .andExpect(jsonPath("$.balanceAfter").value(100)); // body.balanceAfter == 100
}
```

**`jsonPath`** is a JSON selector syntax — `$.type` means "the `type` field at the root." `$.wallets[0].id` means "first element of the `wallets` array's `id` field." It's like XPath but for JSON.

---

## 13. Request Lifecycle — End to End

Trace a `POST /api/wallets/1/deposit` request through every layer:

```
HTTP Request: POST /api/wallets/1/deposit
Body: { "amount": 100.00, "idempotencyKey": "abc-123" }
                │
                ▼
        WalletController.deposit()
        - @PathVariable extracts id=1
        - @Valid runs validation on DepositRequest
          - amount >= 0.01 ✓
          - idempotencyKey not blank ✓
        - calls walletService.deposit(1L, request)
                │
                ▼
        WalletService.deposit()     ← @Transactional: BEGIN TRANSACTION
        - walletRepository.findById(1) → loads Wallet from DB
        - transactionRepository.findByIdempotencyKey("abc-123") → empty
        - wallet.status == ACTIVE → ok
        - balanceBefore = 0.00
        - wallet.balance = 0.00 + 100.00 = 100.00
        - walletRepository.save(wallet) → UPDATE wallets SET balance=100.00 WHERE id=1
        - transactionRepository.save(txn) → INSERT INTO transactions (...)
        - returns TransactionResponse DTO
                │         ← @Transactional: COMMIT
                ▼
        WalletController
        - wraps in ResponseEntity.ok()
                │
                ▼
        HTTP Response: 200 OK
        Body: { "id": 1, "type": "DEPOSIT", "status": "COMPLETED",
                "balanceBefore": 0, "balanceAfter": 100.00, ... }
```

If **validation fails** (e.g. amount = -5):
```
@Valid → MethodArgumentNotValidException thrown
       → GlobalExceptionHandler.handleValidation()
       → 400 Bad Request { "status": 400, "message": "amount: Amount must be at least 0.01" }
```

If **wallet not found**:
```
walletRepository.findById(999) → empty Optional
.orElseThrow(() → WalletNotFoundException("999"))
               → GlobalExceptionHandler.handleNotFound()
               → 404 Not Found { "status": 404, "message": "Wallet not found with id: 999" }
```

If **insufficient funds**:
```
wallet.balance < request.amount
→ throw new InsufficientFundsException(balance, requested)
→ GlobalExceptionHandler.handleInsufficientFunds()
→ 422 Unprocessable Entity { "status": 422, "message": "Insufficient funds: ..." }
→ @Transactional rolls back (no partial writes)
```

---

## Quick Reference — Running the App

```bash
# Build
./mvnw.cmd clean install

# Run (visit http://localhost:8080)
./mvnw.cmd spring-boot:run

# Browse the in-memory database
# open http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:paymentdb  Username: sa  Password: (empty)

# Run all tests
./mvnw.cmd test

# Run a single test class
./mvnw.cmd test -Dtest=WalletServiceTest

# Run a single test method
./mvnw.cmd test -Dtest=WalletServiceTest#deposit_frozenWallet_throwsWalletFrozenException
```
