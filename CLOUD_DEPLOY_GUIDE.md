# Echo Calendar Server Cloud Deploy Guide

## 1) What Gets Deployed

Deploy only the `server/backend` service.  
The batch files in `server\*.bat` are local Windows helpers and are not deployed.

## 2) Fastest Option: Render (No Domain Purchase Needed)

1. Push this repository to GitHub.
2. Open Render dashboard.
3. Click `New +` -> `Blueprint` (recommended) or `Web Service`.
4. Connect your GitHub repo.
5. If using Blueprint, use `server/render.yaml`.
6. If using Web Service manually:
   - Runtime: `Docker`
   - Root Directory: `server/backend`
   - Health Check Path: `/health`
7. Set required environment variables:
   - `OPENAI_API_KEY`
   - `KOREA_HOLIDAY_API_KEY`
8. Optional but recommended env values:
   - `HOLIDAY_SYNC_ENABLED=true`
   - `HOLIDAY_DB_PATH=/var/data/holiday_cache.db`
   - `HOLIDAY_BOOTSTRAP_START_DATE=1970-01-01`
   - `HOLIDAY_BOOTSTRAP_FORWARD_YEARS=5`
   - `HOLIDAY_DAILY_WINDOW_YEARS=1`
9. Deploy.

You can use `server/render.yaml` for IaC-style setup.

## 3) Post-Deploy Checks

Use the generated HTTPS URL from Render.

1. Health:
   - `GET https://<your-render-domain>/health`
2. Holiday API:
   - `GET https://<your-render-domain>/holidays?startDate=2026-01-01&endDate=2026-12-31`
3. AI API sample:
   - `POST https://<your-render-domain>/ai/search-interpret`

Expected:
- `/health` returns `{"status":"ok", ...}`
- `/holidays` returns JSON with `holidays` array

## 4) App Connection

Set app backend URL to the HTTPS server URL.
- Example: `https://<your-render-domain>`

Do not use `10.0.2.2` on real phones. That address is emulator-only.

## 5) Notes

- SSL/TLS is handled by Render by default.
- You can start with Render domain first, then connect a custom domain later.
- Keep API keys only in server environment variables.
- If `HOLIDAY_DB_PATH` is on ephemeral storage, data can reset on redeploy/restart.
- After first deploy, call `/holidays` once to trigger initial cache fill if needed.

## 6) Common Errors

1. `503 NOT_CONFIGURED` on `/holidays`
- `KOREA_HOLIDAY_API_KEY` is missing or empty.

2. `502 UPSTREAM_FAILURE` on `/holidays`
- Public API key invalid, quota issue, or upstream outage.

3. AI endpoints fallback only
- `OPENAI_API_KEY` missing or invalid.

4. App works on emulator but not phone
- App still points to `10.0.2.2` instead of public HTTPS URL.
