# AGENTS.md — Source of Truth (Read Me First)

**You are a fully autonomous engineering agent.** Treat all rules here as system-level, non-negotiable instructions.

## Operating Mode (MUST)

1. **Read this file** before doing anything and keep it loaded as context.
2. **Plan briefly**, then execute changes in **small, reversible steps** with tests.
3. After any change, **run the Gatekeeper**: `./bin/verify.sh`.
    - If it **fails**, analyze the logs, fix the cause, and **run it again**.
    - **Do not** commit or mark done while red.
4. If you lack information, prefer **official docs** and minimal, targeted web searches. Record sources in the PR
   description.
5. **Loop yourself** until all rules pass. Only then commit and open/annotate a PR.
6. **Stop** when the **Definition of Done** below is satisfied—no exceptions.

---

# Single Gatekeeper (MUST create if missing)

Create an executable script at `bin/verify.sh` with **exactly** this content:

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1) Format & static checks
mvn -q spotless:apply
mvn -q -DskipTests=true checkstyle:check

# 2) Build & run all tests (unit + any configured integration tests)
#    'verify' runs checks after tests in the Maven lifecycle.
mvn -q verify

# 3) Repository hygiene
# Fail if formatters changed tracked files and they are not committed yet
if ! git diff --quiet --exit-code; then
  echo "ERROR: Tracked files changed by the build/formatters. Commit them and re-run."
  exit 2
fi

# 4) No to-do/fix-me left in tracked sources (excluding build outputs)
if git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' | grep -q .; then
  echo "ERROR: to-do/fix-me markers present in sources."
  git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' || true
  exit 3
fi

echo "Gatekeeper passed ✅"
```

**Agent duties regarding the gatekeeper:**

- If `bin/verify.sh` is missing, **create it**.
- Always run it from the repo root: `./bin/verify.sh`.
- Never bypass this script with ad-hoc command sequences.

---

# Definition of Done (DoD)

A task is complete only when **all** are true:

- `./bin/verify.sh` exits **0**.
- **Git is clean**: no uncommitted changes (`git status` shows nothing to commit).
- Code is auto-formatted (Spotless applied) and **tests pass**.
- For changed/new public methods, **positive and negative tests** exist.
- Commit message follows the **Commit Rules** below.
- PR description answers **What changed? Why? Breaking changes?** and lists any config/env impacts.

---

# Repository Guidelines

## Project Structure & Module Organization

- `src/main/java`: Application code under `github.ai.qa.solutions` (modules: `nodes`, `tools`, `services`,
  `configuration`, `state`). Entry point: `AgentApplication`.
- `src/main/resources`: App config (e.g., `application.yml`) including model routing and profiles.
- `src/test/java` and `src/test/resources`: Unit/integration tests and fixtures.
- `target/`: Build artifacts (fat JARs, reports).

### Repository Layout

- `/src/main` — monolith spring-boot AI agent based on langgraph4j and spring-ai
- `/src/test` — tests of this spring-boot AI agent

## Build, Test, and Development Commands

- Build runnable JAR: `mvn -DskipTests package` → artifact in `target/`.
- Run locally: `java -jar target/*.jar` (e.g., `SPRING_PROFILES_ACTIVE=gigachat-openrouter java -jar target/app.jar`).
- Run tests: `mvn test` (JUnit 5 / Spring Boot Test).
- Static checks: `mvn -q -DskipTests verify` (includes Spotless check).
- Format sources: `mvn spotless:apply` (Palantir Java Format; removes unused imports).

> **Always prefer** the Gatekeeper: `./bin/verify.sh` instead of running these ad-hoc.

## Coding Style & Naming Conventions

- Java 21. **Formatting is enforced** by Spotless (Palantir). Do **not** hand-tune style—run `spotless:apply`.
- Packages: lower-case dot-separated. Classes: `PascalCase`. Methods/fields: `camelCase`.
- If a field **can be `final`, it must be `final`**; prefer immutable records/configs.
- Keep Spring components small and cohesive; graph nodes under `nodes`, tools under `tools`.

## Testing Guidelines

- Framework: JUnit 5 with Spring Boot Test. Mirror package structure under `src/test/java`.
- Naming: `*Test` for unit tests; `*IT` for integration tests if added.
- Tests must be **fast, deterministic**, and **mock external AI clients**.
- **Coverage expectation**: Every changed method has **positive and negative** tests (at least one of each).

## Commit & Pull Request Guidelines

### Commit Rules (MUST)

- One logical change per commit.
- Subject: **imperative mood**, ≤ **72 chars**. Example: `Add node routing for validation`.
- Include issue refs as needed (`#<id>`). Avoid mixed concerns.
- **Never** commit failing code or formatting-only drift.

### PR Rules (MUST)

Include:

- **What changed?**
- **Why?**
- **Breaking changes?**
- Screenshots/logs for UI/behavioral changes.
- Linked issues and any env/config implications.
- Note that **Gatekeeper is green** and list key tests added.

## Security & Configuration Tips

- Configure providers via env/YAML. Common vars: `OPENROUTER_API_KEY`, `SPRING_AI_GIGACHAT_CLIENT_ID`,
  `SPRING_AI_GIGACHAT_CLIENT_SECRET`, `SPRING_PROFILES_ACTIVE`.
- Model routing per node via `application.yml` under `ai.model-routing.nodes` (e.g.,
  `ReasonAndRouteNode: deepseek/deepseek-r1`).
- **Do not commit secrets** or long-lived tokens. Prefer profile-based overrides for local dev.

---

# Engineering Guidance

## Single Responsibility (SRP)

- One class/module = one reason to change. If the docstring needs “and/or,” split it.
- Keep public APIs tiny. Internal helpers can grow; public surface stays lean.
- Group by **capability** (e.g., `PaymentsService`) not tech (“Utils”).
- Controllers: I/O only (parse/validate/request/response). Services: business. Repos: persistence.
- Favor composition over inheritance; inject collaborators.

## No-Nest Code (Flatten Control Flow)

- **Guard clauses first**; bail early on invalid states.
- **Max indent depth = 2**. If you hit 3, extract a function/object.
- Replace deep if/else with polymorphism, strategy maps, or tables.
- In loops, use `continue`/`return` to avoid nesting.
- Prefer exceptions for exceptional paths over boolean flags.
- Structure workflows: **validate → load → act → persist → map**.

Example:

```java
import lombok.NonNull;

public Receipt buy(@NonNull final User user, @NonNull final Cart cart, @NonNull final Payment payment) {
    if (cart.isEmpty()) throw new IllegalArgumentException("Empty cart");
    if (!payment.isValid()) throw new IllegalArgumentException("Bad payment");
    return charge(cart, payment); // happy path
}
```

## Null-Safety

- Don’t return `null` for collections/optionals — return `List.of()` / `Optional.empty()`.
- Use `Optional<T>` only as a **return type**, not as a field or parameter.
- Constructor injection + `final` fields ⇒ non-null invariants.
- Annotate intent: `lombok.NonNull` and validate at boundaries.
- Prefer Null Object (e.g., `NoopPublisher`) instead of scattered null checks.
- Never log secrets while checking for nulls.

## Javadoc That’s Useful

**Classes**

```java
/**
 * Coordinates payment capture and order creation.
 *
 * <p><b>Invariants:</b> idempotent per orderId; all writes in one transaction.
 * <b>Thread-safety:</b> stateless; safe to reuse.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * var id = checkoutService.place(orderCmd);
 * }</pre>
 *
 * @see PaymentsGateway
 * @implNote Validates stock before charging.
 */
public final class CheckoutService { ...
}
```

**Fields**

```java
/** Max items per cart; 1–500. Defaults to 100. */
private final int maxCartItems = 100;
/** ISO-4217 currency code, never null (e.g., "EUR"). */
private final String currency;
```

**Methods**

```java
import lombok.NonNull;

/**
 * Charges the user and creates an order.
 *
 * @param cmd validated order command; must contain at least one item
 * @return created order id
 * @throws InsufficientFunds if payment fails due to balance
 * @throws NotFound if the cart or user does not exist
 * @implSpec Executes within a transaction; idempotent for the same requestId.
 */
public OrderId place(@NonNull final OrderCmd cmd) { ...}
```

## Logging (SLF4J)

- Parameterized messages (`{}`), no string concatenation.
- Levels:
    - **ERROR**: user-visible failure needs attention.
    - **WARN**: unexpected but handled; monitor.
    - **INFO**: lifecycle/business milestones.
    - **DEBUG/TRACE**: verbose internals for dev.
- Log once per failure at the boundary; avoid re-logging on every layer.
- Use `@Slf4j`; **no PII/secrets**. Redact tokens/IDs.
- Keep it readable—emoji welcome 🤡 (but never replace substance).

## Allure in Tests

- **Labels**: class-level `@Epic`, `@Feature`, `@Owner`; test-level `@Story`, `@Severity`, optional `@TmsLink`/`@Issue`.
  Add `@DisplayName` + 1-line `@Description`.
- **Steps**: annotate helpers `@Step("Add {sku} to cart")`; or `Allure.step("Submit payment", () -> { ... })`.
- **Attachments**: include key artifacts (JSON, screenshots, logs) with correct MIME/ext.
- Keep label values stable (e.g., `Feature="Payments"`). Aim for **2–4 labels/test**. No secrets in attachments.

## Test Coverage Rule

**All methods should be covered by positive and negative tests.**  
You must add/adjust tests when changing behavior, contracts, or edge cases.

---

# Build & Test Quickstart (for humans & agents)

- Format first: `mvn spotless:apply`
- Run all tests: `mvn test`
- Run a single test: `mvn test -Dtest=ai.qa.test.data.generation.agent.SimpleAiGeneratorTest`
- Full build: `mvn clean install`
- **Preferred**: `./bin/verify.sh` (always)

---

# Agent Work Loop (Pseudo-code, MUST follow)

```
READ AGENTS.md
WHILE true:
  PLAN minimal change -> IMPLEMENT
  RUN ./bin/verify.sh
  IF exit == 0:
     PREPARE commit (clean status) + PR summary (What/Why/Breaking)
     STOP
  ELSE:
     ANALYZE failures -> PATCH -> REPEAT (no human approval required)
```

**Forbidden:**

- Committing with red gate, skipping tests, or leaving to-do/fix-me.
- Creating “Utils” grab-bags without domain rationale.
- Hand-tuning formatting instead of running Spotless.
