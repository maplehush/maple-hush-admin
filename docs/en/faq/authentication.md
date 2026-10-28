# Login and Permissions

First find the request that actually failed in browser Network tools. HTTP statuses, business `code` values, and frontend handling exceptions are different information; do not classify every failure as an incorrect password.

## Can ADMIN_BOOTSTRAP_ENABLED reset the administrator password?

**Symptom:** The original password does not change after changing the bootstrap switch or account variables, or restarting the application.

**Check:** Confirm whether the connection uses a new database or one with existing users, and whether the administrator has already been created and completed the initial password change.

**Cause:** Bootstrap creates the first administrator only when there are no users, no super administrator holder, and bootstrap has been explicitly enabled. Existing accounts and passwords are stored in the database and are not overwritten during normal startup. Bootstrap and password reset are different operations.

**Resolution:** For the first startup of an empty database, enable bootstrap as described in [Initial administrator configuration](/en/getting-started/configuration), and disable it after completing the password change. For an existing database, use the saved account and password. If an ordinary user forgets their password, an authorized administrator resets it through the password reset feature. Losing the unique super administrator's password is a dedicated maintenance recovery scenario; do not try to solve it by changing bootstrap variables or writing a plaintext password directly.

**Verification:** After bootstrap is disabled, the existing account can still log in with its saved new password. Rebuilding frontend and backend applications does not reset passwords in the persistent database.

## Why is the CAPTCHA invalid even though I read it correctly?

**Symptom:** Login returns `CAPTCHA_INVALID`, or reusing the same image after one submission fails.

**Check:** Confirm that the submitted `captchaId` matches the current image, whether the image has been displayed too long, and whether submission was repeated. Also check for Redis dependency errors.

**Cause:** The current CAPTCHA is valid for two minutes and is consumed once. Do not assume the same CAPTCHA can be reused after an incorrect username or password.

**Resolution:** Obtain a new CAPTCHA, fill it in again, and avoid repeated clicks. Handle dependency errors as described in [Startup and Configuration](./startup.md). For rate limits, wait before retrying instead of continuously refreshing and flooding the CAPTCHA API.

**Verification:** The new image and ID match. If the next response contains a different error, handle that new error type rather than continuing to treat it as a CAPTCHA problem.

## Why did login return code 0 without opening the workspace?

**Symptom:** The login API returns success, but the page starts a password change flow, or remains on the login page with a session/login handling error.

**Check:** First determine whether response `data` indicates a required password change or a normal session, then inspect the console and subsequent requests. Do not judge the entire login flow complete solely from `code = 0`.

**Cause:** Initial login may issue only dedicated password change credentials. Normal login also involves frontend session handling, user information, and menu loading. Historical versions failed after login on HTTP pages because they depended on `crypto.randomUUID` / Web Locks; the current source has removed this hard dependency. Browsers still need to permit the storage capabilities used by the project.

**Resolution:** If a password change is required, complete it and log in again as described in [First login](/en/getting-started/first-run). For frontend exceptions, check whether the actual running artifacts include the fix, close old tabs and reopen the page, and check whether the browser restricts site storage. If a subsequent API fails, investigate that request rather than directly resetting the password.

**Verification:** A normal session can enter the workspace; after completing the password change branch, the new password can be used to log in. The HTTP compatibility fix does not mean every browser has been tested and does not change the plan to use HTTPS on the production server.

## Why do menu, button, and API permissions behave differently?

**Symptom:** A page is available without buttons, a menu is entirely hidden, or a button is visible but its API returns `FORBIDDEN`.

**Check:** Investigate each symptom below separately and confirm that you are using the intended current account:

| Symptom | Check first |
| --- | --- |
| Menu hidden | Menu and ancestor status, role status and grants, effective menu response |
| Page cannot load | Mapping between component and actual Vue file, backend component allowlist |
| Button hidden | Button permission code and current effective permission codes |
| API returns 40300 | API permissions, Service target scope, and protected-data rules |

**Cause:** Route components, menus, buttons, and server authorization have different responsibilities. Frontend visibility does not guarantee backend permission to operate; authorization changes may also invalidate old sessions.

**Resolution:** Compare permission codes in configuration and code with [Menus and Permissions](/en/development/permissions). Log in again after changes to load current authorization. Do not remove `@PreAuthorize` or merely hide buttons to handle API rejection.

**Verification:** Menus and buttons agree with current authorization, and direct API requests by accounts without permission are still rejected.
