# langchain4j-skills-agent

Spring Boot 3.2.5 REST API that routes user requests to LangChain4j AI skills via OpenRouter (OpenAI-compatible).

## Architecture

```
HTTP Request
    │
    ▼
AgentController        ← REST layer (/agent)
    │
    ▼
AgentDispatcher        ← parses /skillname <args>
    │
    ▼
SkillService           ← loads SKILL.md files, builds AiService
    │
    ▼
SkillAiService         ← LangChain4j AiService interface
    │
    ▼
RefreshableChatModel   ← OpenAI-compatible model, auto-refreshes key on 401
    │
    ▼
OpenRouter API         ← actual LLM call
```

## Tech Stack

| Component     | Version |
|---------------|---------|
| Java          | 21      |
| Spring Boot   | 3.2.5   |
| LangChain4j   | 1.15.1  |
| langchain4j-skills | 1.15.1-beta25 |
| OkHttp3       | 4.12.0  |

## Quick Start

### 1. Set environment variable

```bash
export API-KEY=your_openrouter_api_key
```

### 2. Build and run

```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`.

### 3. Configuration (`application.properties`)

```properties
server.port=8080

openai.base-url=https://openrouter.ai/api/v1
openai.model-name=openai/gpt-oss-120b:free
openai.api-key=${API-KEY}
openai.temperature=0.7
openai.max-tokens=1024

# API key TTL — refreshes on 401 after this window
openai.api-key-ttl-seconds=600

# Classpath root for skill directories
agent.skills.classpath=skills
```

---

## API Reference

Base URL: `http://localhost:8080`

---

### POST `/agent/invoke`

Invoke a skill by sending a slash-command in the request body.

**Request**

```http
POST /agent/invoke
Content-Type: application/json

{
  "input": "/weather London"
}
```

| Field   | Type   | Required | Description                                      |
|---------|--------|----------|--------------------------------------------------|
| `input` | string | yes      | Slash command: `/skillname <args>`. Must not be blank. |

**Response — success**

```json
{
  "output": "{\"cityname\":{\"name\":\"London\",\"country\":\"UK\"},\"temperature\":{\"value\":15,\"unit\":\"celsius\",\"feels_like\":13},\"weather\":{\"cloudy\":true,\"rainy\":false,\"sunny\":false,\"description\":\"Overcast skies\"}}",
  "success": true,
  "error": null
}
```

**Response — error**

```json
{
  "output": null,
  "success": false,
  "error": "error message"
}
```

| Field     | Type    | Description                  |
|-----------|---------|------------------------------|
| `output`  | string  | Skill result (null on error) |
| `success` | boolean | `true` if skill ran OK       |
| `error`   | string  | Error message (null on success) |

**HTTP status codes**

| Code | Meaning                            |
|------|------------------------------------|
| 200  | Skill invoked (check `success` field) |
| 400  | `input` blank or missing           |
| 500  | Unexpected server error            |

---

### GET `/agent/skills`

List all loaded skills.

**Request**

```http
GET /agent/skills
```

**Response**

```json
{
  "output": "[/summarize, /weather]",
  "success": true,
  "error": null
}
```

---

## Skills

Skills live in `src/main/resources/skills/`. Each subdirectory with a `SKILL.md` is auto-loaded at startup via `ClassPathSkillLoader`.

### Invoke syntax

```
/skillname <args>
```

Example:
```
/weather Tokyo
/summarize The quick brown fox jumps over the lazy dog
```

---

### `weather`

Returns current weather for a city as structured JSON.

**Input:** city name  
**Output:**

```json
{
  "cityname": {
    "name": "Tokyo",
    "country": "Japan"
  },
  "temperature": {
    "value": 24,
    "unit": "celsius",
    "feels_like": 23
  },
  "weather": {
    "cloudy": false,
    "rainy": false,
    "sunny": true,
    "description": "Clear skies and warm sunshine"
  }
}
```

Weather flags (`cloudy`, `rainy`, `sunny`) are boolean — exactly one is `true`.

---

### `summarize`

Summarizes provided text.

**Input:** text to summarize  
**Output:** concise summary paragraph

> **Note:** `summarize/SKILL.md` currently contains placeholder content. Update `src/main/resources/skills/summarize/SKILL.md` to define summarization behavior.

---

## Key Design Notes

### Skill dispatch

`AgentDispatcher` expects input in `/skillname <args>` format. Bare text without a leading `/` returns an error prompt listing available skills.

### API key refresh

`RefreshableChatModel` wraps the OpenAI client with double-checked locking. On a `401 Unauthorized` response, it calls `ApiKeyProvider.refresh()` and retries exactly once — no manual restart needed.

### Adding a new skill

1. Create directory: `src/main/resources/skills/<skillname>/`
2. Add `SKILL.md` with YAML frontmatter:
   ```markdown
   ---
   name: skillname
   description: What this skill does and when to use it
   ---

   Instructions for the LLM...
   ```
3. Restart the app — `ClassPathSkillLoader` picks it up automatically.

---

## Project Structure

```
src/main/
├── java/com/test/
│   ├── Application.java
│   ├── config/
│   │   ├── AgentProperties.java       # @ConfigurationProperties(prefix="openai")
│   │   ├── ApiKeyProvider.java        # key fetch + refresh logic
│   │   ├── LangChain4jConfig.java     # ChatModel bean
│   │   └── RefreshableChatModel.java  # auto-refresh wrapper
│   ├── controller/
│   │   └── AgentController.java       # POST /agent/invoke, GET /agent/skills
│   ├── dispatcher/
│   │   ├── AgentDispatcher.java       # /skillname routing
│   │   └── dto/
│   │       ├── AgentRequest.java
│   │       └── AgentResponse.java
│   └── skill/
│       ├── SkillAiService.java        # LangChain4j AiService interface
│       └── SkillService.java          # skill loader + invocation
└── resources/
    ├── application.properties
    └── skills/
        ├── summarize/SKILL.md
        └── weather/SKILL.md
```
