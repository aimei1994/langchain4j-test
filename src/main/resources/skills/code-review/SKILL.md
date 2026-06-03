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

**Confidence filter:** Only flag violations you are confident about from the visible code. If the issue is already guarded or handled in the visible scope (e.g., a null check exists before the dereference, a `break` exists inside a `while (true)`), do not flag it. When in doubt, skip — false negatives are better than false positives in code review.

**Multi-rule violations:** A single line may appear in multiple objects if it violates multiple rules independently.

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
- **Insert-before fix:** If the fix requires inserting new lines before the violation (e.g., adding a null guard before a dereference), include the full rewritten block starting from `startLine`. Renumber all lines in `suggestionCode` sequentially from `startLine`. `startLine` and `endLine` in the object reflect the original violation range only.

---

## Step 5 — Output

Return **ONLY** a JSON array — no prose, no markdown fences, no text outside the JSON.

```json
[
  {
    "ruleName": "<ruleName from reference/rule.md>",
    "severity": "<severity from reference/rule.md>",
    "reason": "<one sentence explaining why this specific code is a violation and what risk it introduces>",
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
| `reason`         | One sentence: what is wrong and what runtime/security risk it introduces. Reference the specific variable or method name from the code. |
| `existingCode`   | Exact violating lines — preserve `N: ` prefix, spaces, tabs                                    |
| `suggestionCode` | Fixed lines in same `N: <source>` format as `existingCode`. Keep `N: ` prefix. Preserve original indentation after the prefix. If fix inserts lines before violation, renumber from `startLine`. If fix appends lines after, continue numbering from last existing line. |
| `startLine`      | Integer — first line number of the violating block                                              |
| `endLine`        | Integer — last line number of the violating block                                               |

- No violations → return `[]`
- Never change logic outside the violation scope
- `suggestionCode` and `existingCode` must have identical prefix format: `N: <source>`

---

## Example

**Input:**
```
1: import java.util.Map;
2: import java.util.HashMap;
3:
4: public class Store {
5:     private Map<String, String> cache = new HashMap<>();
6:
7:     public String get(String key) {
8:         String value = cache.get(key);
9:         return value.toUpperCase();
10:    }
11: }
```

**Output:**
```json
[
  {
    "ruleName": "null pointer exception",
    "severity": "major",
    "reason": "cache.get(key) returns null when key is absent; calling toUpperCase() without a null check causes NullPointerException at runtime.",
    "existingCode": "8:         String value = cache.get(key);\n9:         return value.toUpperCase();",
    "suggestionCode": "8:         String value = cache.get(key);\n9:         if (value == null) { return null; }\n10:        return value.toUpperCase();",
    "startLine": 8,
    "endLine": 9
  }
]
```
