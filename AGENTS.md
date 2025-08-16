# AGENTS.md — Project rules for Codex

**You are an engineering agent working in this repo.** Treat this file as non‑negotiable. Keep changes small and reversible.

---

## Authoritative knowledge policy (MCP‑first)

**Always use the `context7` MCP server as the primary source of truth about APIs, frameworks, language features, and tooling.** Only if `context7` cannot provide relevant results after reasonable querying may you fall back to the model’s own knowledge.

**Mandatory procedure (before writing code, tests, or reviews):**
1. **Identify target tech + version** from the repo (e.g., library versions in `pom.xml`, plugin versions, Java/Maven settings). Use `ripgrep`/`fs` to read files if needed.
2. **Query `context7` first.** Form queries like:
    - `spring boot @3.3 request validation`
    - `maven checkstyle plugin @3.6.0 configuration xml module`
    - `junit jupiter @5.10 parameterized test examples`
      Refine up to **3 attempts** with more precise terms or version pins.
3. **Use what `context7` returns** as the canonical reference. Prefer examples and APIs from the returned docs/specs. Do not contradict them.
4. **If zero relevant results** (or results are clearly off‑topic) **after 3 refined queries → LLM fallback is allowed.**  
   When falling back:
    - State in your answer/PR **“Knowledge source: LLM fallback (context7 had no relevant results)”**.
    - Add links to any official docs you independently used later (if any).
5. **Cite sources in PRs.** When you used `context7`, list the doc titles/links you followed under “Sources used”.

> Hard rule: Do **not** rely on unstated “tribal knowledge”. Either cite `context7` or mark **LLM fallback**.

---

## Run checks (always)
- Work from the repo root.
- Before making changes, perform the **knowledge policy** steps above.
- After each change, run: `./bin/verify.sh`. If it fails, fix and run again. Do **not** commit while red.
- If `bin/verify.sh` is missing, create it with the exact content below (executable).

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1) Format & static checks
mvn -q spotless:apply
mvn -q -DskipTests=true checkstyle:check

# 2) Build & tests
mvn -q verify

# 3) Repo hygiene — fail if formatters changed tracked files
if ! git diff --quiet --exit-code; then
  echo "ERROR: Tracked files changed by the build/formatters. Commit them and re-run."
  exit 2
fi

# 4) No markers left in sources (ignore build, generated, and docs)
if git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' ':!**/*.md' ':!**/*.MD' | grep -q .; then
  echo "ERROR: todo/fixme markers present in sources."
  git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' ':!**/*.md' ':!**/*.MD' || true
  exit 3
fi

echo "Gatekeeper passed ✅"
```

## Definition of Done
A task is complete only when **all** are true:
- `./bin/verify.sh` exits **0**.
- Git is **clean** (`git status` shows nothing to commit).
- Spotless applied; Checkstyle and all tests pass.
- For changed/new **public methods**, add **positive and negative** tests.
- Commit & PR follow the templates below.
- **Knowledge recorded**: PR description lists either `context7` citations used **or** explicitly says *LLM fallback* when applicable.

### Commit message template
- One logical change per commit.
- Subject in **imperative mood**, ≤ **72 chars**. Example: `Add validation node routing`.
- Include issue refs when relevant (`#123`). Avoid mixed concerns.

### PR checklist (put in description)
- **What changed?**  **Why?**  **Breaking changes?**
- Key tests added, logs/screenshots if UI/behavior changed.
- Any env/config impacts.
- State that the **Gatekeeper is green**.
- **Sources used:** list doc titles/links from `context7`. If none were found and LLM knowledge was used, write: **“Knowledge source: LLM fallback (context7 had no relevant results).”**

## Project facts the agent must follow
- Language/runtime: **Java 21**, build with **Maven**.
- Tests: **JUnit 5**; mirror package structure under `src/test/java`.
- Style: enforced by **Spotless (Palantir)** — never hand‑format.
- Static analysis: **Checkstyle**; do **not** edit `configs/checkstyle.xml` or other config in `configs/`.
- Packages: lower‑case dot‑separated; classes `PascalCase`; methods/fields `camelCase`.
- Prefer immutability: make fields `final` when possible; constructor injection.
- External AI/HTTP clients must be **mocked**; tests are fast and deterministic.
- Naming: unit tests end with `*Test`; integration tests (if any) end with `*IT`.

## Tooling guardrails (Codex + MCP)
- **Primary knowledge tool:** `context7`. Use first; refine queries up to 3 times before any fallback.
- **Code search/navigation:** `ripgrep` (fast, precise), `fs` (read/write inside allowed roots).
- **Do not expand `~`** in paths; use absolute paths.
- If a tool fails (timeout/startup), surface the error briefly, then retry once; if still failing, proceed only with what’s already known, and document **LLM fallback** in the PR.
- Never send secrets to tools or into logs. Redact tokens, passwords, keys.

## Guardrails
- No secrets in code, logs, tests, or attachments.
- Do not create “misc Utils” without a clear domain reason.
- Keep controllers thin (I/O only); business logic in services; persistence in repos.
- Avoid deep nesting; use guard clauses and small methods.

---

**Stop** when the DoD above is satisfied. Record any web/MCP sources used in the PR description.
