# Getting Started

This chapter explains how to download the source, prepare a development environment, and start Maple Hush Admin, ending with the administrator's first login and password change.

It assumes basic Java and Vue knowledge and that you can install development tools and manage MySQL and Redis on your system. Windows, Linux, and macOS are supported by these instructions; where commands differ, PowerShell and Bash examples are provided.

## Reading Order

| Step | Page | Goal |
| --- | --- | --- |
| 1 | [Prerequisites](./prerequisites.md) | Tools meet project version requirements, and MySQL/Redis are reachable |
| 2 | [Download and Installation](./installation.md) | Download the full repository, install frontend dependencies, and prepare the backend build |
| 3 | [Local Configuration](./configuration.md) | Create a development database and configure connections and initial administrator bootstrap |
| 4 | [Startup and First Login](./first-run.md) | Start the backend and frontend, log in, change the password, and log in again |

## Runtime Setup Used in This Chapter

- Frontend: Vite development server, default port **5999**.
- Backend: package the source project with Maven Wrapper, then run the Jar; default port **8080**.
- Data services: use prepared MySQL and Redis instances and specify their addresses and credentials through backend parameters.
- Configuration profile: **dev**, allowing local development without a domain or HTTPS certificate.

```text
Browser http://localhost:5999
    → Vite same-origin /api proxy
    → Backend http://127.0.0.1:8080
    → MySQL, Redis
```

This is the local development workflow. Docker, Nginx, HTTPS, and environment isolation for production servers will be covered in [Deployment and Operations](/en/deployment/).

## Before You Start

- Use a new development database, or understand the purpose and administrator status of the existing database.
- Set backend parameters in the terminal that starts it or in the IDE run configuration; a root `.env` is not loaded automatically when starting from source.
- OSS can be configured later. Without it, skip avatar and file uploads; other core management features can start first.
- Replace example paths, accounts, and passwords in commands with your actual values.

This chapter follows the current source and configuration. See [Technology Stack](/en/introduction/technology) for versions and purposes; follow each tool's official instructions for installation on your platform.
