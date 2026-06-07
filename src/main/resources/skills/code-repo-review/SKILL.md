---
name: code-repo-review
description: |
  Scans a project directory for external connections (HTTP, DB, Redis) and checks
  whether unit tests cover retry and reconnect scenarios for each connection type.
  Input: a file path to the project root or a specific module.
  Use when: auditing resilience coverage, reviewing external connection handling,
  checking if retry/reconnect is tested, project-wide connection analysis.
allowed-tools:
  - readFile
  - listFiles
  - grepInDirectory
  - checkDirectory
---

# Code Repo Review — External Connection Resilience Analyzer

## Step 1 — Load rules

Read `reference/connection-rules.md`. Extract:
- Detection keywords for HTTP, DB, Redis per language
- Retry/reconnect test keywords for each connection type
- Severity levels for coverage gaps

---

## Step 2 — Resolve input path and detect language

The user provides a file path. Call `checkDirectory(path)` to confirm it exists.

Detect language by calling `listFiles(path, ".xml")` and checking for `pom.xml` → Java/Kotlin.
Then try `listFiles(path, ".json")` for `package.json` → TypeScript/JS.
Then `listFiles(path, ".mod")` for `go.mod` → Go.
Then `listFiles(path, ".toml")` for `pyproject.toml` → Python.
Then `listFiles(path, ".csproj")` → C#.

Use detected language to select the matching keyword sets from `reference/connection-rules.md`.

---

## Step 3 — Discover source files and test files

Call `listFiles` to enumerate source and test files.

**Java/Kotlin source:** `listFiles(path + "/src/main", ".java")` and `listFiles(path + "/src/main", ".kt")`
**Java/Kotlin tests:** `listFiles(path + "/src/test", ".java")` and `listFiles(path + "/src/test", ".kt")`
**Python source:** `listFiles(path, ".py")` — exclude paths containing `test_` or `_test`
**TypeScript/JS source:** `listFiles(path + "/src", ".ts")` and `listFiles(path + "/src", ".js")`
**Go source:** `listFiles(path, ".go")` — exclude paths ending in `_test.go`
**C# source:** `listFiles(path, ".cs")` — exclude paths containing `.Tests.`

---

## Step 4 — Scan source files for external connections

For each connection type (HTTP, DB, REDIS), call `grepInDirectory` with each detection keyword from `reference/connection-rules.md` matching the detected language.

Example calls:
- `grepInDirectory(path + "/src/main", ".java", "RestTemplate")`
- `grepInDirectory(path + "/src/main", ".java", "JdbcTemplate")`
- `grepInDirectory(path + "/src/main", ".java", "RedisTemplate")`

For each match returned (format `filepath:lineNumber: content`):
- Record `connectionType`, `sourceFile`, `matchedKeyword`, `lineNumber`
- Derive `componentName`: call `readFile(sourceFile)` and find the nearest `class` declaration above the matched line

Group by `componentName` — one component may have multiple connection types.

---

## Step 5 — Scan test files for retry/reconnect coverage

For each discovered `componentName` from Step 4:

1. Call `grepInDirectory(path + "/src/test", ".java", componentName)` to find test files referencing it.
2. For each test file found, call `grepInDirectory` with each retry/reconnect keyword from `reference/connection-rules.md` matching the `connectionType`.
3. Record which retry/reconnect keywords are present.

Coverage assessment per component per connection type:
- **No test file** found referencing component → `critical`
- **Test file exists** but zero retry/reconnect keywords → `major`
- **Retry keyword found** but no timeout OR no backoff keyword → `minor`
- **All three present** (retry + timeout + backoff/policy) → `info`

---

## Step 6 — Output

Return **ONLY** a JSON array — no prose, no markdown fences, no text outside the JSON.

```json
[
  {
    "connectionType": "<HTTP|DB|REDIS>",
    "severity": "<critical|major|minor|info>",
    "componentName": "<class or bean name where connection is used>",
    "sourceFile": "<relative path to source file>",
    "sourceLineNumber": 0,
    "matchedKeyword": "<exact keyword that triggered detection>",
    "testFile": "<relative path to test file, or null if none found>",
    "retryKeywordsFound": ["<list of retry/reconnect keywords found in test>"],
    "missingCoverage": "<one sentence describing what retry/reconnect scenario is not tested>",
    "recommendation": "<one sentence fix: what test to add and which scenario to cover>"
  }
]
```

### Field contracts

| Field | Contract |
|-------|----------|
| `connectionType` | One of: `HTTP`, `DB`, `REDIS` |
| `severity` | `critical` / `major` / `minor` / `info` — from Step 5 coverage assessment |
| `componentName` | Class or bean name; derive from nearest enclosing class declaration above the matched line |
| `sourceFile` | Relative path from the input root |
| `sourceLineNumber` | Integer line number of the connection keyword match |
| `matchedKeyword` | Exact string that matched a detection keyword |
| `testFile` | Relative path of the test file referencing this component, or `null` |
| `retryKeywordsFound` | Array of matched retry/reconnect keywords found in test file; empty array `[]` if none |
| `missingCoverage` | What specific scenario is untested: e.g. "No test for HTTP timeout when remote host is unreachable" |
| `recommendation` | Concrete test suggestion: e.g. "Add test mocking SocketTimeoutException and assert retry is attempted 3 times with exponential backoff" |

- No external connections found → return `[]`
- Components with `severity: info` still appear in output so the report is complete
- Each unique `componentName` + `connectionType` combination = one JSON object
- If a component has both HTTP and DB connections, emit two separate objects

---

## Example

**Input path:** `/myapp`

**Detected:** Java project with `DataSource` in `UserRepository.java`, no test file references `UserRepository`, no retry keywords.

**Output:**
```json
[
  {
    "connectionType": "DB",
    "severity": "critical",
    "componentName": "UserRepository",
    "sourceFile": "src/main/java/com/example/UserRepository.java",
    "sourceLineNumber": 14,
    "matchedKeyword": "JdbcTemplate",
    "testFile": null,
    "retryKeywordsFound": [],
    "missingCoverage": "No test exists for UserRepository DB connection failure or retry on transient DataAccessException.",
    "recommendation": "Create UserRepositoryTest that mocks DataSource to throw TransientDataAccessException and verifies retry is attempted with @Retryable or RetryTemplate."
  }
]
```
