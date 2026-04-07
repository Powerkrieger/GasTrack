import base64
import io
import json
import os
import secrets
import sqlite3
import uuid
from pathlib import Path

import qrcode
from authlib.integrations.starlette_client import OAuth
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from starlette.middleware.sessions import SessionMiddleware

BASE_URL = os.environ["BASE_URL"].rstrip("/")

app = FastAPI(title="GasTrack Sync Server")
app.add_middleware(SessionMiddleware, secret_key=os.environ["SECRET_KEY"])

oauth = OAuth()
oauth.register(
    name="keycloak",
    server_metadata_url=(
        f"{os.environ['KEYCLOAK_URL']}/realms/{os.environ['KEYCLOAK_REALM']}"
        "/.well-known/openid-configuration"
    ),
    client_id=os.environ["KEYCLOAK_CLIENT_ID"],
    client_secret=os.environ["KEYCLOAK_CLIENT_SECRET"],
    client_kwargs={"scope": "openid email profile"},
)

DATA_DIR = Path("data")
PHOTOS_DIR = DATA_DIR / "photos"
DB_PATH = DATA_DIR / "gastrack.db"


def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db():
    DATA_DIR.mkdir(exist_ok=True)
    PHOTOS_DIR.mkdir(exist_ok=True)
    with get_db() as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS devices (
                id   TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                api_key TEXT UNIQUE NOT NULL,
                created_at INTEGER DEFAULT (strftime('%s','now'))
            );
            CREATE TABLE IF NOT EXISTS fuel_entries (
                id              TEXT PRIMARY KEY,
                device_id       TEXT REFERENCES devices(id),
                timestamp       INTEGER NOT NULL,
                latitude        REAL NOT NULL,
                longitude       REAL NOT NULL,
                city            TEXT NOT NULL,
                station_name    TEXT NOT NULL,
                liters          REAL NOT NULL,
                euros           REAL NOT NULL,
                price_per_liter REAL NOT NULL,
                kilometers      REAL NOT NULL DEFAULT 0,
                created_at      INTEGER DEFAULT (strftime('%s','now'))
            );
        """)


init_db()


# ---------------------------------------------------------------------------
# Auth dependency
# ---------------------------------------------------------------------------

async def require_device(x_api_key: str = Header(...)):
    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM devices WHERE api_key = ?", (x_api_key,)
        ).fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return dict(row)


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@app.get("/")
def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Sync endpoint
# ---------------------------------------------------------------------------

ENTRY_COLS = (
    "id, timestamp, latitude, longitude, city, station_name, "
    "liters, euros, price_per_liter, kilometers"
)


@app.post("/sync")
async def sync(request: Request, device: dict = Depends(require_device)):
    body = await request.json()
    known_ids: set = set(body.get("known_ids", []))
    incoming: list = body.get("entries", [])

    with get_db() as conn:
        for entry in incoming:
            conn.execute(
                f"""
                INSERT OR REPLACE INTO fuel_entries
                    (id, device_id, timestamp, latitude, longitude, city,
                     station_name, liters, euros, price_per_liter, kilometers)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    entry["id"], device["id"], entry["timestamp"],
                    entry["latitude"], entry["longitude"], entry["city"],
                    entry["station_name"], entry["liters"], entry["euros"],
                    entry["price_per_liter"], entry["kilometers"],
                ),
            )
            if photo_b64 := entry.get("photo"):
                try:
                    (PHOTOS_DIR / f"{entry['id']}.jpg").write_bytes(
                        base64.b64decode(photo_b64)
                    )
                except Exception:
                    pass

        if known_ids:
            placeholders = ",".join("?" * len(known_ids))
            rows = conn.execute(
                f"SELECT {ENTRY_COLS} FROM fuel_entries WHERE id NOT IN ({placeholders})",
                list(known_ids),
            ).fetchall()
        else:
            rows = conn.execute(
                f"SELECT {ENTRY_COLS} FROM fuel_entries"
            ).fetchall()

    result = []
    for row in rows:
        entry = dict(row)
        photo_path = PHOTOS_DIR / f"{row['id']}.jpg"
        if photo_path.exists():
            entry["photo"] = base64.b64encode(photo_path.read_bytes()).decode()
        result.append(entry)

    return {"entries": result}


# ---------------------------------------------------------------------------
# Pairing web UI  (Keycloak-protected)
# ---------------------------------------------------------------------------

def _make_qr_b64(data: str) -> str:
    img = qrcode.make(data)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


@app.get("/pair/login")
async def pair_login(request: Request):
    redirect_uri = f"{BASE_URL}/pair/callback"
    return await oauth.keycloak.authorize_redirect(request, redirect_uri)


@app.get("/pair/callback")
async def pair_callback(request: Request):
    token = await oauth.keycloak.authorize_access_token(request)
    userinfo = token.get("userinfo") or await oauth.keycloak.userinfo(token=token)
    request.session["user"] = dict(userinfo)
    return RedirectResponse(url="/pair")


@app.get("/pair", response_class=HTMLResponse)
async def pair_page(request: Request):
    if not request.session.get("user"):
        return RedirectResponse(url="/pair/login")

    user = request.session["user"]
    server_url = BASE_URL

    with get_db() as conn:
        devices = conn.execute(
            "SELECT * FROM devices ORDER BY created_at DESC"
        ).fetchall()

    devices_html = ""
    for d in devices:
        qr_b64 = _make_qr_b64(json.dumps({"url": server_url, "key": d["api_key"]}))
        devices_html += f"""
        <div class="device">
            <h3>{d['name']}</h3>
            <img src="data:image/png;base64,{qr_b64}" width="200" alt="QR code">
            <p><small>Key: <code>{d['api_key'][:8]}…</code></small></p>
        </div>
        """

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>GasTrack — Pair Device</title>
  <style>
    body {{ font-family: sans-serif; max-width: 800px; margin: 2rem auto; padding: 1rem; }}
    .device {{ border: 1px solid #ddd; padding: 1rem; margin: 1rem 0; border-radius: 8px; display: inline-block; margin-right: 1rem; vertical-align: top; }}
    input[type=text] {{ padding: .5rem; width: 260px; }}
    button {{ padding: .5rem 1.2rem; background: #1976d2; color: #fff; border: none; border-radius: 4px; cursor: pointer; }}
    button:hover {{ background: #1565c0; }}
    code {{ background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }}
  </style>
</head>
<body>
  <h1>GasTrack — Add Device</h1>
  <p>Logged in as <strong>{user.get("email") or user.get("sub")}</strong></p>
  <form method="post" action="/pair">
    <label>Device name: <input type="text" name="device_name" required placeholder="e.g. Pixel 9"></label>
    <button type="submit">Generate QR Code</button>
  </form>
  <h2>Existing Devices</h2>
  {devices_html or '<p>No devices yet.</p>'}
</body>
</html>"""


@app.post("/pair", response_class=HTMLResponse)
async def pair_create(request: Request):
    if not request.session.get("user"):
        return RedirectResponse(url="/pair/login")

    form = await request.form()
    device_name = str(form.get("device_name", "")).strip()
    if not device_name:
        return RedirectResponse(url="/pair")

    api_key = secrets.token_urlsafe(32)
    device_id = str(uuid.uuid4())
    with get_db() as conn:
        conn.execute(
            "INSERT INTO devices (id, name, api_key) VALUES (?,?,?)",
            (device_id, device_name, api_key),
        )

    server_url = BASE_URL
    qr_b64 = _make_qr_b64(json.dumps({"url": server_url, "key": api_key}))

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>GasTrack — Scan QR</title>
  <style>
    body {{ font-family: sans-serif; max-width: 600px; margin: 2rem auto; padding: 1rem; text-align: center; }}
    a {{ color: #1976d2; }}
  </style>
</head>
<body>
  <h1>Scan this QR code in GasTrack</h1>
  <p>Device: <strong>{device_name}</strong></p>
  <img src="data:image/png;base64,{qr_b64}" width="300" alt="Pairing QR code">
  <p><a href="/pair">← Back to devices</a></p>
</body>
</html>"""
