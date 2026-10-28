# Startup and Configuration

These questions assume [running locally from source](/en/getting-started/). The default frontend port is 5999 and the backend port is 8080; actual startup output takes precedence.

## Why does the backend still use the original configuration after I create .env?

**Symptom:** You create `.env` in the repository root or backend directory, change connection parameters, and start the Jar, but the application still connects to the old address.

**Check:** Determine whether startup uses `java -jar`, an IDE, or Compose, and whether parameters were configured for the actual process. Do not print complete environment variable values in logs.

**Cause:** Starting a Jar directly does not automatically read these `.env` files. Compose variable loading, Vite `.env*`, and Java process environments are separate mechanisms. An already open IDE may not inherit new variables from another terminal either.

**Resolution:** For Jar startup, set environment variables in the same terminal. For IDE startup, set variables and the dev profile in the relevant run configuration, then restart the backend. See [Local Configuration](/en/getting-started/configuration) for parameters.

**Verification:** The new process activates the expected profile and connects to the intended services. Java source does not need recompiling when only runtime parameters change.

## What should I check first when MySQL or Redis connections fail?

**Symptom:** Backend startup or CAPTCHA requests report connection failure, authentication failure, or unavailable required dependencies.

**Check:** Distinguish connection timeouts/refusals, account authentication failures, missing databases, and migration failures in logs. Check the actual host, port, account, and database name for each. `localhost` means the machine or network environment running the application; it does not automatically refer to another machine.

**Cause:** Project defaults do not install services, create databases, or change existing service passwords. Determine the specific connection failure from its logs. Redis also participates in CAPTCHAs and rate limiting, so confirming only MySQL availability is insufficient.

**Resolution:** Configure the actual services and restart the backend. Create the database itself beforehand; let Flyway manage the table structure. If development Redis has no password, explicitly override the default as described in [Redis connection configuration](/en/getting-started/configuration), rather than merely setting an empty environment variable that the terminal may remove.

**Verification:** The backend starts normally, readiness is UP, and the CAPTCHA API succeeds. If errors remain, retain the first sanitized exception for further diagnosis instead of clearing the database or Redis by trial and error.

## Why do requests or login fail after changing frontend or backend ports?

**Symptom:** The page opens, but `/api` cannot connect, or login-related requests fail origin validation.

**Check:** Inspect the actual frontend URL, backend listening port, Vite proxy target, and the failed request's HTTP status and business code. Proxy reachability and origin validation failures need separate handling.

**Cause:** The default proxy points to `127.0.0.1:8080`; the default allowed frontend Origins include port 5999. Changing one side's port does not update the other. Currently, `VITE_API_PROXY_TARGET` is read by `vite.config.ts` from the startup process environment.

**Resolution:** Set the proxy target in the frontend startup terminal and restart Vite. Update backend `SERVER_PORT` and actual `AUTH_ALLOWED_ORIGINS`, then restart the backend. Follow [Custom ports or backend addresses](/en/getting-started/first-run), and consistently use the same hostname throughout.

**Verification:** `/api` reaches the intended backend and origin validation passes. Do not disable permission or CSRF checks to resolve port configuration problems.

## Where are Knife4j and the error code explanations?

**Symptom:** You cannot find API documentation, or can open it but cannot find shared error code explanations.

**Check:** Confirm that the backend has started, its actual port, and the profile. Distinguish a page that cannot open from one that opens but where the explanation has not been found.

**Cause:** Documentation is disabled by default and enabled only in development and test configuration; it is disabled in production. Shared error explanations are centralized on the documentation homepage instead of repeating the complete code table under every API.

**Resolution:** Local dev defaults to `http://127.0.0.1:8080/doc.html`, with OpenAPI at `/v3/api-docs` on the same port. View general error responses on the Knife4j "Home" page, or read [Shared error codes](/en/standards/api#shared-error-codes) directly. If the port has changed, use the actual port.

**Verification:** The dev documentation page is accessible and its homepage contains the shared explanations. The absence of a production documentation entry is intentional configuration; do not enable it temporarily for debugging.
