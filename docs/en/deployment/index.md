# Deployment & Operations

::: info To be completed
This section currently defines the deployment scope and reading path. The complete deployment procedure has not yet been verified on a real server. Detailed instructions will follow when a server and domain are available.
:::

## Target Environment

This section targets **Ubuntu 24.04 LTS servers** and covers test and production environments. The test environment validates releases before they reach production. Their access endpoints, configuration, credentials, and business data must be isolated.

For local development from source, read [Getting Started](/en/getting-started/). The deployment guide will begin with a fresh server; it will not assume Docker or a gateway is already installed.

## Planned Topics

| Stage | Topics |
| --- | --- |
| Server preparation | System initialization, deployment accounts, network access, and basic tools |
| Installation and download configuration | Docker, Compose, Git, Nginx, and the selection and configuration of dependency sources |
| Project configuration | Code directories, environment variables, file-based secrets, and persistent data |
| Initial deployment | Image builds, service startup, database initialization, and administrator bootstrap |
| Access endpoints | DNS, Nginx reverse proxy, HTTPS, and trusted proxy configuration |
| Environment isolation | Separate domains, configuration, credentials, storage, and data for test and production |
| Routine operations | Health checks, logs, backups, and troubleshooting |

These topics are planned. They do not yet form a complete step-by-step deployment procedure.

## Deployment and Release Documentation

This section covers environment preparation, initial deployment, and routine operations. [Releases & Upgrades](/en/release/) will cover code updates, pipelines, releases, database upgrades, and rollback.

For the distinction between current code checks and the future release pipeline, see [Git Workflow](/en/standards/git).
