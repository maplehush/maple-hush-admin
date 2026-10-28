# Download and Installation

## Download the Complete Repository

Run in the parent directory where you want to store the project.

```sh
git clone --branch main --single-branch https://github.com/maplehush/maple-hush-admin.git
cd maple-hush-admin
git branch --show-current
git status --short
```

Expected: the current branch is `main`, with no local workspace changes. `main` is the starting point for reading and using the project; contributors should use `develop` or the relevant feature branch according to the team's workflow.

Do not download only `frontend/apps/web-antdv-next/`: it depends on shared packages and build configuration in the frontend workspace. The repository root should contain `frontend/`, `backend/`, `docs/`, and other directories.

If the directory already exists, first confirm it is the repository you intend to use. Do not clone over it or discard existing changes to follow this tutorial.

## Install Frontend Dependencies

Run in the repository's `frontend/`, not `docs/` or the target application's subdirectory.

```sh
cd frontend
node --version
pnpm --version
pnpm install --frozen-lockfile
```

Expected: Node meets the project range, pnpm is **11.16.0**, and installation finishes with exit code 0. The first install also runs workspace preparation scripts; duration depends on the network and machine.

`--frozen-lockfile` installs from the existing repository lockfile, preventing installation from resolving a different set of versions. If installation fails, retain the error output and first distinguish version mismatches, network download failures, and dependency script problems. Do not retry by deleting the lockfile.

This project uses `pnpm` directly. Do not substitute `corepack pnpm install`, to avoid child scripts using a global pnpm version that differs from the project.

## Check Maven Wrapper

Open another terminal and enter the repository's `backend/`.

::: code-group

```powershell [PowerShell]
.\mvnw.cmd --version
```

```sh [Bash]
sh ./mvnw --version
```

:::

Expected: Maven **3.9.9**, Java **21**. POSIX examples explicitly use `sh`, so the checked-out `mvnw` does not need executable permissions.

The first run downloads the Maven distribution specified by the Wrapper. Do not change `distributionUrl` or upgrade the parent POM just to use the project. Check the network, proxy, and JDK first if the download fails.

## Prepare the Backend Build

Still in `backend/`, run:

::: code-group

```powershell [PowerShell]
.\mvnw.cmd -DskipTests package
```

```sh [Bash]
sh ./mvnw -DskipTests package
```

:::

Expected: `BUILD SUCCESS`, producing:

```text
backend/admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

This command builds all four modules. `-DskipTests` is used for this local quick-start route; it does not mean project tests have passed. Checks required during development are explained in the development standards.

Do not run `*.jar.original` or start a core, api, or biz module Jar; the only application entry point is `admin-system-start`.

## Next Step

Complete [Local Configuration](./configuration.md) before starting the application. Successful packaging alone does not mean MySQL, Redis, or the administrator account is ready.
