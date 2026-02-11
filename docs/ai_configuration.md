# AI Configuration (No key committed)

Set properties in `~/.gradle/gradle.properties` or project `local.properties` (already gitignored):

```properties
AI_API_BASE_URL=https://your-api.example.com
AI_API_KEY=your-secret
# Optional overrides
AI_API_BASE_URL_DEBUG=https://dev-api.example.com
AI_API_BASE_URL_RELEASE=https://prod-api.example.com
AI_API_KEY_DEBUG=
AI_API_KEY_RELEASE=
AI_API_TIMEOUT_MS=12000
```

The app reads these values into `BuildConfig` and uses them in `HttpAiApiGateway`.
When URL/key are missing or request fails, the app automatically falls back to local interpreter logic.
