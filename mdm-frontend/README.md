# MDM Frontend

Standalone UI dashboard for operating the MoveInSync MDM backend without Postman.

## Run Locally

From `D:\moveinsync\mdm-frontend`:

```powershell
python -m http.server 5500
```

Open:

```text
http://localhost:5500
```

## Backend Connection

- Default backend base URL in the UI: `http://localhost:8080`
- Admin credentials (default): `admin` / `admin123`
- Device API key (default): `moveinsync-device-key`

Make sure the backend app is running before using API actions from the dashboard.
