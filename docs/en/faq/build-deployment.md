# Builds and Code Updates

This page initially covers toolchains, builds, and configuration loading. Production server deployment troubleshooting will be added after the [Deployment and Operations](/en/deployment/) tutorials are validated.

## Why do frontend and docs use different pnpm versions?

**Symptom:** Running pnpm in different directories shows different versions, or frontend installation reports an incompatible tool version.

**Check:** Confirm the current directory first, then inspect that project's `package.json`, `packageManager`, and `engines`. Check `node --version` and `pnpm --version` in the same directory.

**Cause:** frontend and docs are independent projects, pinning pnpm 11.16.0 and 12.3.4 respectively. The frontend also depends on the complete workspace; installing only the application subdirectory is insufficient. The confirmed local method is to run pnpm directly, avoiding `corepack pnpm install` subprocess scripts falling back to a different global version.

**Resolution:** Follow [Download and Installation](/en/getting-started/installation) for the frontend, using the pinned version and existing lockfile in `frontend/`. Documentation uses the independent configuration in `docs/`. Do not delete lockfiles or change packageManager to bypass mismatches.

**Verification:** Each project uses its declared version, and installation does not unexpectedly modify the lockfile. Matching versions do not guarantee installation success; distinguish network failures from dependency-script errors as well.

## Do dependency download timeouts mean the code is faulty?

**Symptom:** Installation or builds time out while downloading pnpm, npm packages, a Maven distribution, or dependencies.

**Check:** Find the specific URL, failing stage, and execution environment in the error. Determine whether downloading the tool itself or business dependencies failed. Host accessibility does not guarantee accessibility from another build environment.

**Cause:** A timeout means only that this access attempt did not complete within the limit; it does not directly establish an error in code or the lockfile. Corepack downloads pnpm before pnpm installs dependencies, so changing only pnpm's registry may not cover the preceding step.

**Resolution:** Check the failing environment's network, DNS, proxy, and actual download source. Configure through the current project's entry point. The current frontend Dockerfile already connects the `NPM_REGISTRY` build argument through `COREPACK_NPM_REGISTRY` and `npm_config_registry`; changing the host's global registry does not establish that configuration inside the image has changed. Production server source configuration steps remain to be added to deployment documentation.

**Verification:** The previously failing download stage succeeds and the build continues; address subsequent compilation errors separately. Do not delete lockfiles, disable TLS verification, or configure unknown mirrors as long-term dependency sources to resolve a timeout.

## Why is old content still running after code changes?

**Symptom:** You modify or pull source code, but pages, APIs, or configuration remain unchanged after restarting.

**Check:** Determine whether the actual runtime uses a development server, Jar, static dist, or container image, and check the startup path and current commit. Identify the artifact used by the process before repeatedly editing source that is not being loaded.

**Causes and resolutions:** Different runtime modes load code at different times:

| Runtime mode | Action after changes |
| --- | --- |
| Vite development server | Ordinary Vue/TS changes usually hot-reload; restart the development server after startup environment or proxy configuration changes |
| `java -jar` | Repackage and start the new Jar after Java or packaged resource changes; restart the process after runtime parameter changes |
| Built frontend dist | Rebuild and ensure the actual static service serves the new artifacts |
| Container image | Build a new image and update the container after source changes; restart does not compile source, and `--no-build` explicitly skips building |

**Verification:** The current process or static entry point uses the latest artifacts and the original change is visible. Pulling code alone or restarting an old Jar or image does not update the application. See [Download and Installation](/en/getting-started/installation) and [Development Standards](/en/standards/) for build commands.
