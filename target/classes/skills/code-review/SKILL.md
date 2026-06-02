---
name: code-review
description: |
  Reviews code for rule violations and outputs structured JSON findings.
  Supports any language. Rules loaded dynamically from reference/rule.md.
  Use when: reviewing code, auditing code quality, code review, code audit.
allowed-tools:
  - Read
  - Grep
---

# Code Review — Rule-Based Analyzer

## Step 1 — Load rules

Read `reference/rule.md`. Extract every rule entry:
- `ruleName`
- `severity`
- `instruction` (includes language-specific patterns — follow them exactly)

Apply all rules found. Do not skip any. Do not apply rules not in that file.

---

## Step 2 — Detect language

Identify the programming language from the code syntax and keywords.
Each rule's `instruction` already contains language-specific guidance — use the detected language to select the right patterns from the instruction.

---

## Step 3 — Scan and flag violations

For each rule, scan the full input code top-to-bottom and flag every location that matches the rule's `instruction` for the detected language. One JSON object per violation.

---

## Step 4 — Parse line numbers and strip prefixes

Input code lines use the format `N: <source>` — digit(s), colon, single space, then the raw source line with its own indentation:

```
1: import xxxx
2:
3: public class Foo {
4:     void bar(String s) {
5:         if (s.isEmpty()) {}
6:     }
7: }
```

Rules:
- `startLine` / `endLine` → the integer `N` values only
- `existingCode` → copy lines verbatim including the `N: ` prefix and all whitespace
- `suggestionCode` → same `N: ` prefix format as `existingCode`. Write the fixed source lines, each prefixed with its line number exactly as `N: <fixed source line>`. Preserve original indentation after the prefix. Do NOT strip or omit the `N: ` prefix.

---

## Step 5 — Output

Return **ONLY** a JSON array — no prose, no markdown fences, no text outside the JSON.

```json
[
  {
    "ruleName": "<ruleName from reference/rule.md>",
    "severity": "<severity from reference/rule.md>",
    "existingCode": "<exact violating lines from input, preserving N: prefix and all whitespace/tabs>",
    "suggestionCode": "<fixed lines with N: prefix preserved, same format as existingCode — e.g. '2:     public int foo() {\\n3:         return 0;\\n4: }'>",
    "startLine": 0,
    "endLine": 0
  }
]
```

### Field contracts

| Field            | Contract                                                                                        |
|------------------|-------------------------------------------------------------------------------------------------|
| `ruleName`       | Exact `ruleName` string from `reference/rule.md`                                               |
| `severity`       | Exact `severity` string from `reference/rule.md`                                               |
| `existingCode`   | Exact violating lines — preserve `N: ` prefix, spaces, tabs                                    |
| `suggestionCode` | Fixed lines in same `N: <source>` format as `existingCode`. Keep `N: ` prefix. Preserve original indentation after the prefix. If fix adds new lines, continue numbering from last existing line. |
| `startLine`      | Integer — first line number of the violating block                                              |
| `endLine`        | Integer — last line number of the violating block                                               |

- No violations → return `[]`
- Never change logic outside the violation scope
- `suggestionCode` and `existingCode` must have identical prefix format: `N: <source>`
