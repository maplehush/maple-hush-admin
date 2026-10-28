# Frequently Asked Questions

This section collects common questions with clear supporting evidence in the current project. It applies to the repository's existing source code and configuration. Production server deployment questions will be added after the corresponding processes are validated.

## Find a symptom

| Category | Common symptoms |
| --- | --- |
| [Startup and Configuration](./startup.md) | .env has no effect, database or Redis connection failures, port changes, API documentation cannot be found |
| [Login and Permissions](./authentication.md) | Bootstrap cannot reset a password, invalid CAPTCHA, no redirect after successful login, menu and API permissions differ |
| [Builds and Code Updates](./build-deployment.md) | Different pnpm versions, dependency download timeouts, old code still running after source changes |

## Suggested troubleshooting order

1. Confirm the current directory, Git revision, application actually running, and access URL.
2. Find the first useful error in startup logs, distinguishing compilation failures, startup failures, and request failures.
3. For failed requests, inspect the specific HTTP status and response in browser Network tools; do not rely only on the page message.
4. Follow the relevant entry to check runtime configuration, request flow, or permissions instead of repeatedly reinstalling dependencies, resetting passwords, or clearing data.
5. Reload according to the actual runtime mode after making changes, then check whether the original symptom has disappeared.

The "Verification" in these pages is the check readers should perform after handling a problem. It does not mean every machine has already been tested. For first use, read [Getting Started](/en/getting-started/) first.

## What to include in a problem report

Provide reproduction steps, current branch and commit, tool versions, startup method, the failed request path / HTTP status / business error code, and relevant sanitized logs. Do not provide passwords, tokens, Cookies, complete environment files, or database backups.

See [API Standards](/en/standards/api#shared-error-codes) for shared error codes. [Deployment and Operations](/en/deployment/) and [Releases and Upgrades](/en/release/) are currently reserved for future completion.
