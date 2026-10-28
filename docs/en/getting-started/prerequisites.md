# Prerequisites

Prepare the following tools and services before downloading and starting the project. Version requirements come from repository configuration; you do not need to install a complete frontend framework or Spring Boot separately on the host.

## Tools and Versions

| Tool or Service | Project Requirement / Selected Version | Purpose |
| --- | --- | --- |
| Git | A version capable of cloning GitHub repositories | Retrieve and update code |
| JDK | **21** | Compile and run the backend; a full JDK is required, not just a JRE |
| Node.js | **^22.18.0 or ^24.12.0** | Frontend toolchain; frontend .node-version / CI uses 24.16.0 |
| pnpm | **11.16.0** | Fixed by packageManager in frontend/package.json |
| Maven | Wrapper fixed at **3.9.9** | Use repository scripts; no global Maven installation required |
| MySQL | Project deployment target: **8.4 LTS** | Database and Flyway migrations |
| Redis | Project deployment target: **7.4** | CAPTCHAs, rate limits, and caches |

Node.js `^22.18.0` means 22.x at least 22.18.0; `^24.12.0` means 24.x at least 24.12.0. Do not use a version outside the project range simply because another Node major version is newer.

Database server versions differ from JDBC / Redis client dependency versions. See the [Technology Stack](/en/introduction/technology) for the full dependency versions.

## Install Tools

- JDK 21: install a JDK for your system, set `JAVA_HOME`, and add its `bin` directory to PATH.
- Node.js: use the [official download page](https://nodejs.org/en/download) or an existing version manager to install a version in the range above.
- Git: confirm that `git` is available in the terminal after installation.
- pnpm: once compatible Node/npm is available, install the fixed version:

```sh
npm install --global pnpm@11.16.0
```

If pnpm is already installed, enter the downloaded `frontend/` directory and check the actual version there. The project's `packageManager` may make pnpm switch automatically to the fixed version. Do not rely solely on the global version outside the repository. [Official pnpm installation instructions](https://pnpm.io/installation)

`docs/` is an independent documentation project with a different fixed pnpm version. This chapter starts `frontend/`.

## Check Tools

Run in any terminal.

```sh
git --version
java -version
javac -version
node --version
npm --version
```

Expected: both Java and javac are version 21, and Node is within the allowed range. Reopen the terminal after installing or switching versions, then check again. The IDE's Project SDK and runtime JDK must also point to 21.

## Prepare MySQL and Redis

Use local services or existing dedicated development instances, and record:

| MySQL | Redis |
| --- | --- |
| Address, port, and database name | Address, port, and database number |
| Username and password | Password |
| DDL / DML permissions on this development database | A separate business key prefix |

Example MySQL client connection; enter the password interactively:

```sh
mysql --host=127.0.0.1 --port=3306 --user=root --password
```

After connecting, run `SELECT VERSION();` to confirm this is the intended instance.

Example Redis 7 client connection:

```sh
redis-cli -h 127.0.0.1 -p 6379 --askpass PING
```

Expected response: `PONG`. For a standalone development Redis without a password, omit `--askpass`; see [Local Configuration](./configuration.md#redis-connection) for backend configuration without a password. Older clients may not support this option; use the connection method supported by your client.

Redis installation options vary by system; follow the [official installation instructions](https://redis.io/docs/latest/operate/oss_and_stack/install/). On Windows, historical unofficial Redis 3.x packages are not the project's recommended version; use a supported approach or an existing development instance. Container installation and server deployment are outside this chapter.

## Network Preparation

The first installation needs access to GitHub, npm package registries, and Maven repositories. The frontend repository's `.npmrc` configures an npm mirror by default; Maven Wrapper also downloads a Maven distribution.

Proxy settings may differ between the host network, IDE, and individual tools. If downloads fail, check the specific repository, network, and proxy first. Do not delete lockfiles or disable TLS verification.

Continue to [Download and Installation](./installation.md) when ready.
