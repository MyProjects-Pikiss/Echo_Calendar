# AI Configuration (No key committed)

Echo Calendar supports remote AI suggestions via a backend gateway.

## 1) Where to configure

Set properties in `~/.gradle/gradle.properties` or project `local.properties` (gitignored).

```properties
AI_API_BASE_URL=https://your-api.example.com
AI_API_KEY=your-secret

# Optional overrides
AI_API_BASE_URL_DEBUG=https://dev-api.example.com
AI_API_BASE_URL_RELEASE=https://prod-api.example.com
AI_API_KEY_DEBUG=
AI_API_KEY_RELEASE=
AI_API_TIMEOUT_MS=12000

# Security toggles
AI_SEND_CLIENT_API_KEY_DEBUG=false
AI_SEND_CLIENT_API_KEY_RELEASE=false
AI_REQUIRE_HTTPS_DEBUG=false
AI_REQUIRE_HTTPS_RELEASE=true
```

Debug build default behavior in this repository:

- `AI_API_BASE_URL` defaults to `http://10.0.2.2:8088` when not set.
- `AI_SEND_CLIENT_API_KEY_DEBUG` defaults to `false`.
- Cleartext HTTP is enabled only for debug.

## 2) Security policy

Recommended production policy:

- `AI_SEND_CLIENT_API_KEY_RELEASE=false`
- App calls **only your backend** in release.
- LLM provider key is stored/used on the server side only.
- Release requests should use HTTPS endpoint (`AI_REQUIRE_HTTPS_RELEASE=true`).

If `AI_API_BASE_URL` is missing or the request fails, the app automatically falls back to local interpreter logic.

## 3) Local E2E smoke setup (without backend)

You can run the local contract stub from this repository:

```bash
python tools/ai_contract_server.py
```

Then point debug build to it:

```properties
AI_API_BASE_URL_DEBUG=http://10.0.2.2:8088
AI_SEND_CLIENT_API_KEY_DEBUG=false
```

- Emulator uses `10.0.2.2` to reach host machine.
- For real device, use your machine LAN IP.

## 4) Build environment note

If Gradle fails with `IllegalArgumentException: 25.0.1`, your Gradle daemon is running on JDK 25.
Until the toolchain stack fully supports it, set Gradle JDK to 17 or 21.


## 5) Backend contract quick check

After starting backend (or local stub), run:

```bash
python tools/check_ai_backend_contract.py --base-url http://127.0.0.1:8088
```

This validates required keys/mode values for all 3 endpoints.
