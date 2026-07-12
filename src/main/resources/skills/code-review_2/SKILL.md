---
name: code-review_2
description: |
  Reviews code for empty/log-only else blocks and empty/swallowed catch blocks,
  outputting structured JSON findings. Supports any language.
  Rules loaded dynamically from reference/rule.md.
  Use when: reviewing code, auditing error handling, code audit.
allowed-tools:
  - Read
  - Grep
---

# Code Review 2 — Error-Handling Analyzer

## Step 1 — Load rules

Read `reference/rule.md`. It defines exactly two rules:
- `empty else block with only logging`
- `empty catch block`

Extract for each: `ruleName`, `severity`, `instruction` (includes language-specific patterns — follow them exactly).

Apply only these two rules. Do not apply any other rule, and do not fall back on
general security-review knowledge (no SQL injection, XSS, hard-coded credentials, etc. —
those belong to other skills, not this one).

---

## Step 2 — Detect language

Identify the programming language from the code syntax and keywords.
Each rule's `instruction` already contains language-specific guidance — use the detected language to select the right patterns from the instruction.

---

## Step 3 — Scan and flag violations

For each of the two rules, scan the full input code top-to-bottom and flag every location that matches the rule's `instruction` for the detected language. One JSON object per violation.

**Confidence filter:** Only flag violations you are confident about from the visible code. When in doubt, skip — false negatives are better than false positives in code review.

**Multi-rule violations:** A single line may appear in multiple objects if it violates both rules independently.

---

## Step 4 — Parse line numbers and strip prefixes

Input code lines use the format `N: <source>` — digit(s), colon, single space, then the raw source line with its own indentation:

```
1: import xxxx
2:
3: public class Foo {
4:     void bar(String s) {
5:         try {
6:             doWork();
7:         } catch (Exception e) {}
8:     }
9: }
```

Rules:
- `startLine` / `endLine` → the integer `N` values only
- `existingCode` → copy lines verbatim including the `N: ` prefix and all whitespace
- `suggestedCode` → same `N: ` prefix format as `existingCode`. Write the fixed source lines, each prefixed with its line number exactly as `N: <fixed source line>`. Preserve original indentation after the prefix. Do NOT strip or omit the `N: ` prefix.

---

## Step 5 — Output

Return **ONLY** the structured findings — no prose, no markdown fences, no text outside the JSON.

### Field contracts

| Field                   | Contract                                                                                        |
|-------------------------|---------------------------------------------------------------------------------------------------|
| `ruleName`               | Exact `ruleName` string from `reference/rule.md` — either `empty else block with only logging` or `empty catch block` |
| `severity`               | Exact `severity` string from `reference/rule.md`                                                 |
| `existingCode`           | Exact violating lines — preserve `N: ` prefix, spaces, tabs                                      |
| `suggestedCode`          | Fixed lines in same `N: <source>` format as `existingCode`                                       |
| `suggestedDescription`   | One sentence: what is wrong and the fix, referencing the specific method/block                   |

- No violations → return an empty findings list
- Never change logic outside the violation scope

---

## Example

**Input:**
```
1: public class Store {
2:     public void save(String key, String value) {
3:         try {
4:             db.put(key, value);
5:         } catch (Exception e) {
6:         }
7:     }
8: }
```

**Output (findings):**
```json
[
  {
    "ruleName": "empty catch block",
    "severity": "medium",
    "existingCode": "5:         } catch (Exception e) {\n6:         }",
    "suggestedCode": "5:         } catch (Exception e) {\n6:             log.error(\"Failed to save key: \" + key, e);\n7:             throw new RuntimeException(e);\n8:         }",
    "suggestedDescription": "catch block for db.put swallows the exception with no logging or rethrow, hiding save failures."
  }
]
```
