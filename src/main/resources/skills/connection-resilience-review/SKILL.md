---
name: connection-resilience-review
description: Scans HTTP/DB/Redis/FTP/Redis connection setup code for missing retry/reconnect and checks unit test coverage. Use when resilience audit, retry coverage review, or connection hardening.
allowed-tools:
  - readFile
  - listFiles
  - grepInDirectory
  - checkDirectory
---

# Connection Resilience Review

## Step 1 — Load rules

Read `reference/resilience-rules.yaml`. For each entry in `code-review-rules` extract:

- `ruleName`, `severity`, `pass` (1 or 2), `detect`, `fix`

Rules with `pass: 1` → source-gap rules (used in Step 3).
Rules with `pass: 2` → test-gap rules (used in Step 4).

**Principle:** Only flag connection establishment / client configuration code where a connection object or client is
built or instantiated. Never flag API call methods: `.getForObject`, `.query`, `.save`, `.execute`, `.get`, `.set`,
`fetch`, `axios.get`, `requests.get` — those are connection usage, not connection setup.

---

## Step 2 — Detect language and locate roots

Call `checkDirectory(inputPath)`. Run probes in order — first match wins. Record `sourceExt` for Step 4 greps.

| Probe                                                | Language      | sourceRoot                  | testRoot                    | sourceExt |
|------------------------------------------------------|---------------|-----------------------------|-----------------------------|-----------|
| `grepInDirectory(inputPath, ".xml", "modelVersion")` | Java/Kotlin   | `{inputPath}/src/main/java` | `{inputPath}/src/test/java` | `.java`   |
| `grepInDirectory(inputPath, ".json", "\"scripts\"")` | TypeScript/JS | `{inputPath}/src`           | `{inputPath}/src`           | `.ts`     |
| `grepInDirectory(inputPath, ".toml", "tool.poetry")` | Python        | `{inputPath}`               | `{inputPath}/tests`         | `.py`     |
| `grepInDirectory(inputPath, ".toml", "edition =")`   | Rust          | `{inputPath}/src`           | `{inputPath}/tests`         | `.rs`     |
| `grepInDirectory(inputPath, ".mod", "module ")`      | Go            | `{inputPath}`               | `{inputPath}`               | `.go`     |
| `grepInDirectory(inputPath, ".csproj", "Project")`   | C#            | `{inputPath}`               | `{inputPath}`               | `.cs`     |

---

## Step 3 — Source scan (run ALL four — do not skip any)

You MUST run all four blocks below. Check each one completely before moving to the next.

**3A — HTTP** (`http-no-retry` detect block):
- Split detected language's keywords on `|`. Call `grepInDirectory(sourceRoot, sourceExt, keyword)` once per keyword.
- For each matched file: `readFile`, extract class/struct name, check body for retry guard keywords.
- No guard → `sourceHandled=false`, emit pass:1 finding. Guard present → `sourceHandled=true`.

**3B — DB** (`db-no-reconnect` detect block):
- Same process as 3A using DB setup keywords and reconnect guard keywords.

**3C — Redis** (`redis-no-reconnect` detect block):
- Same process as 3A using Redis setup keywords and reconnect guard keywords.

**3D — FTP** (`ftp-no-reconnect` detect block):
- Same process as 3A using FTP setup keywords and reconnect guard keywords.

**Early exit:** if 3A + 3B + 3C + 3D all returned zero file matches → return `[]`.

Add every matched component to the **components list**: `(componentName, ruleName, sourceFile, sourceHandled)`.

---

## Step 4 — Test coverage (run for EVERY component in the list)

Do not skip components. Process each one completely.

1. `grepInDirectory(testRoot, sourceExt, componentName)` to locate test file.
2. **No test file** → emit `no-test-file` finding: `existingCode = null`, `suggestionCode` = test stub from `no-test-file` `fix` block, lines numbered from 1.
3. **Test file found** → `readFile(testFilePath)`. Scan for test retry keywords from the matching `pass: 2` rule's `detect` block.
4. **Keywords present** → PASS. No finding.
5. **No keywords** → emit pass:2 finding:
   - `existingCode` = first test method body, `N: ` prefix format
   - `suggestionCode` = that method unchanged + new retry/exhaustion test methods from `fix` block, line numbers continuing sequentially

**Decision matrix:**

| sourceHandled | Test coverage | Findings |
|---|---|---|
| false | no test file | pass:1 rule + `no-test-file` |
| false | file exists, no retry | pass:1 rule + pass:2 rule |
| true | no test file | `no-test-file` only |
| true | file exists, no retry | pass:2 rule only |
| true | file exists, retry covered | none |

---

## Step 4.5 — Self-check before output

Confirm all of the following before proceeding to Step 5:
- [ ] Ran HTTP greps (3A) — recorded result (matches or no matches)
- [ ] Ran DB greps (3B) — recorded result
- [ ] Ran Redis greps (3C) — recorded result
- [ ] Ran FTP greps (3D) — recorded result
- [ ] Checked test coverage for every component in the list

If any box is unchecked, complete it now.

---

## Step 5 — Output

Return **ONLY** a JSON array — no prose, no markdown fences, no text outside the JSON.
pass:1 findings first, then pass:2 findings. No findings → return `[]`.

```json
[
  {
    "ruleName": "<ruleName from resilience-rules.yaml>",
    "severity": "<severity from resilience-rules.yaml>",
    "reason": "<one sentence: which component, what is missing (retry/reconnect/test), and what risk it introduces>",
    "existingCode": "<exact connection setup lines from source file, N: prefix format — null for no-test-file findings>",
    "suggestionCode": "<fixed lines in N: prefix format — for no-test-file: full test stub with lines numbered from 1>",
    "startLine": 0,
    "endLine": 0,
    "sourceFile": "<relative path from project root to source or test file>"
  }
]
```

