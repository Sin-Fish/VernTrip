# VernFlow Admin

## Environment

The admin app now reads shared environment variables from the project root:

- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env`
- `D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\.env.local`

`VITE_API_PROXY_TARGET` and `VITE_ADMIN_PORT` can be configured there.

Independent admin console for VernFlow governance.

## Development

```powershell
cd D:\Aeolyn\MaraudersMap\Work\Computer\project\VernFlow\vernflow-admin
npm install
npm run dev
```

The Vite dev server proxies `/api/*` to `http://localhost:8080`.

## Default login

The login page reads credentials from backend env vars:

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`

Defaults in local development are `admin` / `admin123`.
