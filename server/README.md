# GasTrack Sync Server

Self-hosted sync server for the GasTrack Android app. Bidirectional sync of fuel entries (including receipt photos) across multiple devices, secured with per-device API keys. All traffic is HTTPS via automatic Let's Encrypt certificates.

## Prerequisites

- A Linux server with Docker and Docker Compose
- A domain name pointing to your server (e.g. `gastrack.example.com`)
- A running Keycloak instance (for the device-pairing web UI)

---

## 1. Keycloak Client Setup

1. Log in to your Keycloak admin console
2. Select your realm (or create one)
3. Go to **Clients** → **Create client**
4. Fill in:
   - **Client ID**: `gastrack-server` (or any name — you'll put it in `.env`)
   - **Client authentication**: ON
   - **Authentication flow**: Standard flow only
5. Under **Valid redirect URIs**, add:
   ```
   https://<domain>/pair/callback
   ```
6. Under **Web origins**, add:
   ```
   https://<domain>
   ```
7. Click **Save**
8. Go to the **Credentials** tab and copy the **Client secret**

---

## 2. Server Setup

**Clone or copy this `server/` directory to your server, then:**

### Configure environment

```sh
cp .env.example .env
```

Edit `.env`:

```env
BASE_URL=https://gastrack.example.com        # public URL of this server (no trailing slash)
KEYCLOAK_URL=https://auth.example.com        # your Keycloak base URL (no trailing slash)
KEYCLOAK_REALM=master                        # your realm name
KEYCLOAK_CLIENT_ID=gastrack-server           # client ID from step 1
KEYCLOAK_CLIENT_SECRET=abc123...             # client secret from step 1
SECRET_KEY=...                               # run: openssl rand -hex 32
```

> **Why `BASE_URL`?** The server runs behind a reverse proxy (Caddy), so it can't reliably detect its own public address from incoming requests. Setting `BASE_URL` explicitly ensures the correct redirect URI is sent to Keycloak.

### Configure domain

Edit `Caddyfile` — replace `<domain>` with your actual domain:

```caddyfile
gastrack.example.com {
    reverse_proxy app:8000
}
```

### Start

```sh
docker compose up -d
```

Caddy will automatically obtain a TLS certificate from Let's Encrypt. Make sure ports 80 and 443 are open in your firewall.

Verify the server is running:
```sh
curl https://<domain>/
# {"status":"ok"}
```

---

## 3. Adding a Device (Pairing)

1. Open `https://<domain>/pair` in your browser
2. Log in with your Keycloak credentials
3. Enter a name for the device (e.g. `Pixel 9`) and click **Generate QR Code**
4. Open GasTrack on your phone → **History** tab → gear icon (⚙) → **Scan QR**
5. Point the camera at the QR code — the app configures itself automatically

Repeat for each device. Each device gets its own API key, so you can revoke individual devices by deleting them from the database without affecting other devices.

To re-pair a device (e.g. after a reinstall), visit `/pair` again and scan the existing QR code for that device, or generate a new one.

---

## 4. Manual App Configuration

If QR scanning isn't available, you can configure the app manually:

- **Server URL**: `https://<domain>`
- **API Key**: shown in the `/pair` page as `Key: xxxxxxxx…` — contact your server admin for the full key, or query the database directly:
  ```sh
  docker compose exec app python3 -c "
  import sqlite3
  conn = sqlite3.connect('data/gastrack.db')
  for row in conn.execute('SELECT name, api_key FROM devices'):
      print(row[0], row[1])
  "
  ```

---

## 5. Data & Backups

All data is stored in `./data/`:
- `data/gastrack.db` — SQLite database with all entries
- `data/photos/` — receipt photos (named `<entry-uuid>.jpg`)

Back up the entire `data/` directory to preserve everything.

---

## API Reference

All endpoints require the header `X-Api-Key: <device-api-key>`, except the `/pair/*` routes.

### `GET /`
Health check.
```json
{"status": "ok"}
```

### `POST /sync`
Bidirectional sync. The app sends entries it hasn't pushed yet and all entry IDs it knows about. The server returns entries the device doesn't have.

**Request body:**
```json
{
  "known_ids": ["uuid1", "uuid2"],
  "entries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "timestamp": 1712345678000,
      "latitude": 52.3676,
      "longitude": 4.9041,
      "city": "Amsterdam",
      "station_name": "Shell Overtoom",
      "liters": 40.5,
      "euros": 65.00,
      "price_per_liter": 1.604,
      "kilometers": 450.0,
      "photo": "<base64-encoded JPEG, optional>"
    }
  ]
}
```

**Response:**
```json
{
  "entries": [
    {
      "id": "...",
      "timestamp": 1712300000000,
      "latitude": 52.3,
      "longitude": 4.9,
      "city": "Amsterdam",
      "station_name": "BP",
      "liters": 35.0,
      "euros": 55.00,
      "price_per_liter": 1.571,
      "kilometers": 380.0,
      "photo": "<base64-encoded JPEG, if available>"
    }
  ]
}
```

### `GET /pair`
Web UI for generating device QR codes. Requires Keycloak authentication.

### `GET /pair/login`
Initiates the Keycloak OIDC login flow.

### `GET /pair/callback`
OAuth2 callback — handled automatically, redirects back to `/pair`.
