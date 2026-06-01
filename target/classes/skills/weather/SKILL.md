---
name: weather
description: Returns weather data for a given city as structured JSON. Use when user asks about weather, temperature, or conditions for any city or location.
---

Given a city name, return weather info as JSON only — no prose, no explanation outside the JSON block.

## Output format

```json
{
  "cityname": {
    "name": "London",
    "country": "UK"
  },
  "temperature": {
    "value": 18,
    "unit": "celsius",
    "feels_like": 16
  },
  "weather": {
    "cloudy": true,
    "rainy": false,
    "sunny": false,
    "description": "Overcast skies with light cloud cover"
  }
}
```

## Rules

- `weather` flags are boolean — exactly one of `cloudy`, `rainy`, `sunny` should be `true` (pick dominant condition)
- `temperature.value` is integer, `unit` always `"celsius"`
- If city unknown or ambiguous, still return best estimate with a note in `description`
- Return raw JSON block only — no surrounding text
