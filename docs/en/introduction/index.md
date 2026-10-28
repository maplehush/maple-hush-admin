# Project Overview

Maple Hush Admin is an administration starter project based on Vue, Vben, and Spring Boot. It provides user, role, menu, department, dictionary, logging, and authentication capabilities as a starting point for business systems.

Frontend code, backend code, documentation, and deployment configuration share one Git repository. The frontend targets Vben's `apps/web-antdv-next`; the backend uses four Maven modules assembled into a single Spring Boot process.

## Who This Project Is For

- Developers with Java and Vue fundamentals who want to understand and run the project quickly.
- Teams that need core administration capabilities and plan to extend the project with business modules.
- Readers who want to learn frontend/backend integration, authentication and authorization, and modular development through a complete project.

Start with the [Technology Stack](./technology.md), then read [System Architecture](./architecture.md) and [Directory Structure](./directory.md). To run locally, follow [Getting Started](/en/getting-started/) in order.

## Implemented Features

| Area | Current Capabilities |
| --- | --- |
| Authentication | Username, password, and server-generated image CAPTCHA; mandatory initial password change on first login |
| Session management | JWT access tokens, refresh token rotation, logout, and session invalidation after authorization changes |
| User management | Search, create, edit, delete, status maintenance, role assignment, and administrator password resets |
| Role management | Role maintenance, menu authorization, and protection of built-in roles |
| Menu management | Menu and button node maintenance, dynamic routes, and permission code configuration |
| Department management | Department tree, create/edit/delete operations, and protection against deleting referenced departments |
| Dictionary management | Dictionary type and item maintenance, enabled-item queries, colors, and default-item configuration |
| Log queries | Paginated login and operation logs, retention, and cleanup |
| Personal center | Editing your profile, changing your password, and updating your avatar |
| File upload | Backend validation and upload quotas, integrated with Alibaba Cloud OSS |
| API debugging | Knife4j Next / OpenAPI documentation in development and test environments |

System management includes seven pages: users, roles, menus, departments, dictionaries, login logs, and operation logs. The personal center has a separate entry. Feature availability also depends on the current account's permissions, environment configuration, and external services.

## Permission Model

Authorization is organized around users, roles, menus, and permission codes:

- Users receive menu and operation permissions through roles.
- The frontend generates dynamic routes from menus returned by the backend and displays buttons according to permissions.
- The backend independently checks API permissions; hiding buttons cannot replace server authorization.
- Ordinary users can be created without assigned roles; having no business authorization does not grant system management permissions.

For code entry points, see [Authentication and Authorization Responsibilities](./architecture.md#authentication-and-authorization-responsibilities).

## Current Delivery Scope

| Scope | Status and Boundaries |
| --- | --- |
| Management features and authentication | Frontend/backend implementations and local regression records exist; writing this documentation does not constitute a fresh acceptance check of every feature |
| OSS uploads | Integration code exists; the actual bucket, RAM permissions, and upload flow still require acceptance checks in the user's environment |
| Docker | Frontend/backend image definitions and Compose orchestration exist; the complete production server launch process still requires verification |
| Test and production pipelines | GitHub Actions is planned; existing workflows check code, while automatic deployment and production approvals are not yet implemented |
| Documentation website | Complete Chinese and English editions; project introduction, getting started, development guide, and development standards have content; FAQs are being expanded; deployment/operations and releases/upgrades remain to be completed |
| Announcement management tutorial | A teaching example in the development guide, currently not a built-in business feature |

Do not assume upstream frontend demos are integrated features of this project. Extend new business functionality on the existing foundation, using actual code and acceptance results to determine the scope.

## Engineering Characteristics

- **Modular monolith**: Maven modules separate responsibilities, avoiding the need for multiple deployed services at the outset.
- **Separate frontend and backend**: Communication uses same-origin `/api`, with clear entry points for pages and business APIs.
- **Consistent conventions**: Responses, pagination, error handling, database migrations, and permission codes have shared implementations.
- **Documentation alongside code**: User tutorials live in `docs/`; internal designs and acceptance records remain in `开发文档/`.

Repository: [maplehush/maple-hush-admin](https://github.com/maplehush/maple-hush-admin).
