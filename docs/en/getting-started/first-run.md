# Startup and First Login

This page uses the default ports. Complete [Prerequisites](./prerequisites.md), [Download and Installation](./installation.md), and [Local Configuration](./configuration.md) first.

## 1. Start the Backend

Run in the `backend/` terminal where the environment variables are already set.

```sh
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

Expected logs include `Started AdminApplication` and `Maple Hush Admin started successfully!`. Confirm that dev is active and that there are no database, Redis, or Flyway errors.

Check in the browser:

| Address | Expected Result |
| --- | --- |
| http://127.0.0.1:8080/actuator/health/readiness | status is UP |
| http://127.0.0.1:8080/api/auth/captcha | code is 0; data contains captchaId and imageBase64 |
| http://127.0.0.1:8080/doc.html | Development API documentation |

The JSON CAPTCHA endpoint returns image data, not a webpage displaying an image directly; the login page renders it.

If the backend reports an invalid unique-super-administrator state, first check that it is connected to the intended new database and that bootstrap is enabled for its first initialization. Do not solve this by emptying the database.

## 2. Start the Frontend

Open another terminal and enter the repository's `frontend/`:

```sh
pnpm dev:antdv-next
```

The target frontend application reads these files by default:

```text
frontend/apps/web-antdv-next/.env
frontend/apps/web-antdv-next/.env.development
```

Development configuration already sets `VITE_PORT=5999`, `VITE_GLOB_API_URL=/api`, and `VITE_NITRO_MOCK=false`. The default proxy forwards `/api` to `http://127.0.0.1:8080`; database and Redis passwords do not belong in the frontend.

The terminal should show a local address. By default, open `http://localhost:5999/`; `http://127.0.0.1:5999/` also works. Keep the same address throughout a workflow to avoid separate cookies and browser storage caused by switching hostnames.

If the actual startup port is not 5999, resolve the port conflict or synchronize configuration as below before trying to log in on another port.

### Customize Ports or the Backend Address

For example, to use backend port 8081 and frontend port 6000:

1. Set `SERVER_PORT=8081` and `AUTH_ALLOWED_ORIGINS=http://localhost:6000,http://127.0.0.1:6000` in the backend startup environment, then start the backend.
2. Set the proxy target in the frontend startup terminal and explicitly specify the port through the CLI:

::: code-group

```powershell [PowerShell]
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8081'
pnpm --filter @vben/web-antdv-next dev --port 6000 --strictPort
```

```sh [Bash]
export VITE_API_PROXY_TARGET=http://127.0.0.1:8081
pnpm --filter @vben/web-antdv-next dev --port 6000 --strictPort
```

:::

The current `vite.config.ts` reads `VITE_API_PROXY_TARGET` from `process.env`, so set it in the startup terminal. Writing it only in `.env.development.local` does not establish that the proxy configuration has changed. `--strictPort` fails immediately if the port is occupied.

## 3. First Login and Password Change

Only for the first bootstrap of an empty database:

1. Enter the administrator account on the frontend login page; the default is **admin**.
2. Enter temporary password **12345678** and the image CAPTCHA displayed on the page.
3. After login, the initial password-change page opens. Enter and confirm your new personal password.
4. After the change succeeds, return to the login page, get a new CAPTCHA, and log in with the new password.
5. Confirm that the workbench opens and that you can access system management pages allowed for the current administrator.

The new password must have 8–128 characters and at most 72 bytes when UTF-8 encoded. Do not reuse the old password or common weak passwords. The temporary password is only for the initial change, not a fixed everyday password.

A CAPTCHA expires after one use; refresh it after a failed login. The initial password-change page holds a short-lived dedicated credential. If refreshing the page loses that credential, return to login to obtain it again.

For an existing database, use the existing account and saved password; changing startup variables does not change its password.

## 4. Disable Initial Bootstrap

After the initial password change and successful login, press Ctrl+C in the backend terminal to stop the application, then set:

::: code-group

```powershell [PowerShell]
$env:ADMIN_BOOTSTRAP_ENABLED = 'false'
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

```sh [Bash]
export ADMIN_BOOTSTRAP_ENABLED=false
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

:::

Use the same backend terminal to retain the other variables configured earlier. For IDE startup, edit the run configuration and restart. Normal accounts are stored in the database; disabling bootstrap does not delete them.

## 5. Check the Startup Result

- [ ] Backend readiness returns UP.
- [ ] The frontend displays a CAPTCHA, and API requests reach the real backend through same-origin /api.
- [ ] The initial password has been changed, and the new password opens the workbench.
- [ ] System menus and pages appear according to the current account's permissions.
- [ ] You can log in again after logging out through the avatar menu.
- [ ] Initial bootstrap is disabled, and restarts use the same initialized database and new password.

These are checks to perform in your local runtime; reading the tutorial alone does not complete acceptance. Skip upload checks until OSS is configured.

## Stop and Start Again

Stop the frontend and backend with Ctrl+C in their respective terminals. Before starting again, confirm MySQL and Redis are available, then start the backend and frontend. Keep bootstrap disabled for an existing database.

Vite normally hot-reloads frontend source changes. After changing backend source, repackage and restart the Jar. Restarting an old Jar does not load newly compiled Java code.
