---
name: connection-resilience-review
description: |
  Scans a project directory for two classes of resilience gap:
  (1) Source files where an HTTP/DB/Redis/FTP connection is established or client is built,
      but no retry or reconnect configuration is present.
  (2) Test files for those components that do not test retry or reconnect scenarios.
  Outputs a unified JSON array in code-review format with existingCode and suggestionCode.
  Input: absolute path to the project root or module.
  Use when: resilience audit, retry coverage review, connection hardening, external connection testing.
allowed-tools:
  - readFile
  - listFiles
  - grepInDirectory
  - checkDirectory
---

# Connection Resilience Review

## CRITICAL SCOPE RULE

This skill inspects **connection setup and client creation** only.
- SCAN for: code that **builds or instantiates** an HTTP client, DB pool/connection, Redis factory/client, or FTP client.
- SKIP entirely: API call methods (`getForObject`, `query`, `save`, `execute`, `get`, `set`, `fetch`, `axios.get`, `requests.get`, etc.) — these are connection *usage*, not connection *setup*.

If a file only calls methods on an already-built client, skip that file. Only flag files that **create** the client or connection object.

---

## Step 1 — Load rules

Read `reference/resilience-rules.md`. Extract for each rule:
- `ruleName`, `severity`
- Connection setup detection keywords (Java/Kotlin section only, unless language detected otherwise)
- Retry/reconnect guard keywords
- Fix suggestion template

---

## Step 2 — Detect language and locate source/test roots

Call `checkDirectory(inputPath)`. Then:
- `grepInDirectory(inputPath, ".xml", "modelVersion")` → finds `pom.xml` → **Java/Kotlin**; source root = `{inputPath}/src/main/java`, test root = `{inputPath}/src/test/java`
- `grepInDirectory(inputPath, ".json", "\"scripts\"")` → finds `package.json` → **TypeScript/JS**; source root = `{inputPath}/src`, test root = `{inputPath}/src`
- `grepInDirectory(inputPath, ".toml", "tool.poetry")` → **Python**
- `grepInDirectory(inputPath, ".mod", "module ")` → **Go**
- `grepInDirectory(inputPath, ".csproj", "Project")` → **C#**

---

## Step 3 — PASS 1: Scan source for connection setup missing retry/reconnect

Run one grep per connection type using ONLY the **connection setup detection keywords** from `reference/resilience-rules.md`. Do NOT use API call methods as detection keywords.

Grep calls (Java example):
- HTTP: `grepInDirectory(sourceRoot, ".java", "WebClient.builder()")`, `grepInDirectory(sourceRoot, ".java", "new RestTemplate(")`, `grepInDirectory(sourceRoot, ".java", "HttpClient.newBuilder()")`, `grepInDirectory(sourceRoot, ".java", "OkHttpClient.Builder()")`, `grepInDirectory(sourceRoot, ".java", "HttpClients.custom()")`, `grepInDirectory(sourceRoot, ".java", "OpenAiChatModel.builder()")`, `grepInDirectory(sourceRoot, ".java", "@FeignClient")`
- DB: `grepInDirectory(sourceRoot, ".java", "new HikariDataSource(")`, `grepInDirectory(sourceRoot, ".java", "HikariConfig")`, `grepInDirectory(sourceRoot, ".java", "DriverManager.getConnection(")`, `grepInDirectory(sourceRoot, ".java", "DataSourceBuilder.create()")`
- Redis: `grepInDirectory(sourceRoot, ".java", "new LettuceConnectionFactory(")`, `grepInDirectory(sourceRoot, ".java", "new JedisConnectionFactory(")`, `grepInDirectory(sourceRoot, ".java", "RedisClient.create(")`, `grepInDirectory(sourceRoot, ".java", "new JedisPool(")`, `grepInDirectory(sourceRoot, ".java", "new Jedis(")`
- FTP: `grepInDirectory(sourceRoot, ".java", "new FTPClient()")`, `grepInDirectory(sourceRoot, ".java", "ftpClient.connect(")`, `grepInDirectory(sourceRoot, ".java", "new JSch()")`, `grepInDirectory(sourceRoot, ".java", "new FTPSClient(")`

For each match:
1. Call `readFile(filePath)` — source with `N: ` line number prefix.
2. Extract the **enclosing class**: find the class declaration containing the matched line.
3. Scan the entire class body for any **retry/reconnect guard keywords** from `reference/resilience-rules.md` matching the connection type.
4. If NO guard keyword found → emit finding:
   - `existingCode` = the connection setup block (from `new`/`.builder()` to closing `)` or `;`) — preserve `N: ` prefix
   - `suggestionCode` = the same block rewritten with retry/timeout config from the fix template — same `N: ` format
5. If guard keyword IS found → skip (handled).

---

## Step 4 — PASS 2: Scan tests for missing retry/reconnect test coverage

For each component (class name) flagged in PASS 1:

1. `grepInDirectory(testRoot, ".java", componentClassName)` to find test files.
2. **No test file** → emit `no-test-file`, `severity: critical`. `existingCode = null`. `suggestionCode` = complete new test class stub from `reference/resilience-rules.md` template, line-numbered from 1.
3. **Test file found** → `readFile(testFilePath)`. Scan for **test retry keywords** from `reference/resilience-rules.md` matching the connection type.
4. **No retry keyword in test** → find the first `@Test` method body in the test file. Emit finding:
   - `existingCode` = that `@Test` method — preserve `N: ` prefix
   - `suggestionCode` = existing method unchanged + NEW test method(s) appended after it, testing retry and exhaustion scenarios, from the suggestion template — continue line numbers sequentially

---

## Step 5 — Output

Return **ONLY** a JSON array — no prose, no markdown fences, no text outside the JSON.
PASS 1 findings first, then PASS 2 findings.

```json
[
  {
    "ruleName": "<http-no-retry | db-no-reconnect | redis-no-reconnect | ftp-no-reconnect | http-retry-untested | db-reconnect-untested | redis-reconnect-untested | ftp-reconnect-untested | no-test-file>",
    "severity": "<critical|major>",
    "reason": "<one sentence: class name, connection type, what retry/reconnect scenario is missing>",
    "existingCode": "<exact lines with N: prefix intact — null only for no-test-file>",
    "suggestionCode": "<fixed lines in N: prefix format — for no-test-file: full new test class starting at line 1>",
    "startLine": 0,
    "endLine": 0,
    "sourceFile": "<path relative to inputPath using / separator>"
  }
]
```

### Field contracts

| Field | Contract |
|-------|----------|
| `ruleName` | One of the 9 exact rule names above |
| `severity` | `critical` or `major` — from `reference/resilience-rules.md` |
| `reason` | Names the class, the connection type, and the specific missing guard or test scenario |
| `existingCode` | Exact source lines verbatim with `N: ` prefix. `null` only for `no-test-file` |
| `suggestionCode` | Same `N: <source>` format. Line numbers sequential from `startLine`. For `no-test-file` start from 1 |
| `startLine` | First line number of `existingCode`. `0` for `no-test-file` |
| `endLine` | Last line number of `existingCode`. `0` for `no-test-file` |
| `sourceFile` | Relative path from `inputPath` |

- No findings → `[]`
- Never flag API call methods — only connection setup
- One `componentName + connectionType` = one PASS 1 object + one PASS 2 object (if test gap exists)
- `suggestionCode` must not alter logic outside the violation scope

---

## Example — PASS 1: http-no-retry

`RefreshableChatModel.java` creates `OpenAiChatModel.builder()...build()` at lines 61–68. No `connectTimeout`, `RetryTemplate`, or `@Retryable` anywhere in the class.

```json
[
  {
    "ruleName": "http-no-retry",
    "severity": "critical",
    "reason": "RefreshableChatModel builds an OpenAiChatModel HTTP client at line 61 with no connectTimeout or retry policy — a network timeout or 5xx error after the single manual 401-retry will propagate immediately to the caller.",
    "existingCode": "61:         return OpenAiChatModel.builder()\n62:                 .baseUrl(properties.getBaseUrl())\n63:                 .apiKey(apiKey)\n64:                 .modelName(properties.getModelName())\n65:                 .temperature(properties.getTemperature())\n66:                 .maxTokens(properties.getMaxTokens())\n67:                 .build();",
    "suggestionCode": "61:         return OpenAiChatModel.builder()\n62:                 .baseUrl(properties.getBaseUrl())\n63:                 .apiKey(apiKey)\n64:                 .modelName(properties.getModelName())\n65:                 .temperature(properties.getTemperature())\n66:                 .maxTokens(properties.getMaxTokens())\n67:                 .timeout(Duration.ofSeconds(30))\n68:                 .maxRetries(3)\n69:                 .build();",
    "startLine": 61,
    "endLine": 67,
    "sourceFile": "src/main/java/com/test/config/RefreshableChatModel.java"
  }
]
```

## Example — PASS 2: no-test-file

No test file references `RefreshableChatModel`.

```json
[
  {
    "ruleName": "no-test-file",
    "severity": "critical",
    "reason": "RefreshableChatModel has no test file — the 401-retry path, timeout behavior, and key-refresh logic are entirely untested.",
    "existingCode": null,
    "suggestionCode": "1:  package com.test.config;\n2:  \n3:  import org.junit.jupiter.api.Test;\n4:  import org.junit.jupiter.api.extension.ExtendWith;\n5:  import org.mockito.InjectMocks;\n6:  import org.mockito.Mock;\n7:  import org.mockito.junit.jupiter.MockitoExtension;\n8:  import dev.langchain4j.model.chat.request.ChatRequest;\n9:  import dev.langchain4j.model.chat.response.ChatResponse;\n10: \n11: import java.net.SocketTimeoutException;\n12: \n13: import static org.junit.jupiter.api.Assertions.*;\n14: import static org.mockito.Mockito.*;\n15: \n16: @ExtendWith(MockitoExtension.class)\n17: class RefreshableChatModelTest {\n18: \n19:     @Mock ApiKeyProvider keyProvider;\n20:     @Mock AgentProperties properties;\n21:     @InjectMocks RefreshableChatModel model;\n22: \n23:     @Test\n24:     void shouldRetryOnce_whenDelegateThrows401() {\n25:         ChatRequest req = mock(ChatRequest.class);\n26:         when(keyProvider.getKey()).thenReturn(\"old-key\");\n27:         when(keyProvider.refresh()).thenReturn(\"new-key\");\n28:         // first call throws 401, second succeeds after key refresh\n29:         ChatResponse expected = mock(ChatResponse.class);\n30:         // verify rebuild occurs and second call succeeds\n31:         assertDoesNotThrow(() -> model.chat(req));\n32:     }\n33: \n34:     @Test\n35:     void shouldPropagateException_whenNot401() {\n36:         ChatRequest req = mock(ChatRequest.class);\n37:         assertThrows(RuntimeException.class, () -> model.chat(req));\n38:     }\n39: }",
    "startLine": 0,
    "endLine": 0,
    "sourceFile": "src/test/java/com/test/config/RefreshableChatModelTest.java"
  }
]
```
