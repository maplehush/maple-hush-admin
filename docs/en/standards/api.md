# API Standards

The frontend and backend collaborate through matching request paths, field types, responses, and permission codes. See [API Integration](/en/development/api-integration) for the integration process for new features.

## Paths, methods, and input

Browsers access the backend through the same-origin `/api` path. Controllers declare the complete API path. The frontend `requestClient` already configures the `/api` prefix, so business API files specify only the remaining path.

The existing role APIs provide naming examples:

| Method | Complete path | Purpose |
| --- | --- | --- |
| GET | `/api/system/role/list` | Paginated query |
| GET | `/api/system/role/{id}` | Details |
| POST | `/api/system/role` | Create |
| PUT | `/api/system/role/{id}` | Update |
| DELETE | `/api/system/role/{id}` | Delete |

GET uses query parameters; write operations send JSON or multipart as defined by the API. DTOs accept only fields that may be changed. The backend validates types, lengths, enums, ranges, and business rules. Do not expose all server-managed IDs, built-in flags, permission versions, and similar fields for writing simply for frontend convenience.

## Standard response

Ordinary business APIs use `Result<T>`:

```json
{
  "code": 0,
  "data": { "id": "123" },
  "error": null,
  "message": "ok"
}
```

| Field | Meaning |
| --- | --- |
| `code` | Stable business code: `0` means success and a nonzero value means failure |
| `data` | Business result; may be `null` when there is no result or the request fails |
| `error` | Stable error identifier; `null` on success |
| `message` | Safe message for display; not a stable condition for program logic |

**The HTTP status and business code are two separate pieces of information.** For example, HTTP 401 corresponds to business code `40100`; they are not the same number. Do not use `200` as the success business code or wrap every business exception in HTTP 200.

### Authentication refresh is an existing exception

The success body of `POST /api/auth/refresh` is an access token represented as a JSON string, rather than `Result<T>`. The authentication transport layer handles this separately. Do not wrap the refresh request again in the business request layer or parse this response as `data.accessToken`.

If initial login returns the password-change-required branch, complete the specified password change flow before entering a normal session. Seeing `code = 0` alone does not mean a regular access token has been issued.

## Pagination, IDs, and time

Current list queries typically use `page` and `pageSize`, with page numbers starting at 1. API validation determines the maximum page size. Paginated results use `items` and `total` inside `data`:

```json
{
  "code": 0,
  "data": { "items": [], "total": 0 },
  "error": null,
  "message": "ok"
}
```

| Data | Database / Java | JSON / TypeScript |
| --- | --- | --- |
| Resource IDs and relationship IDs | Signed `BIGINT` / `Long` | Decimal string / `string` |
| Page number, page size, total | Integer | Number |
| Enabled status | Integer as defined by the table | Such as `0` or `1`, rather than `"0"` / `"1"` |
| Date and time | Time type specified by the project | `yyyy-MM-dd HH:mm:ss` string |

JavaScript numbers cannot represent every Long value exactly. Keep IDs as strings in transmission, comparison, routes, and forms; do not convert them to numbers and back. Whether a particular parent field accepts `0` or `null` depends on that API. Do not generalize a root-node convention to all IDs.

Handle time consistently using the `Asia/Shanghai` convention. Do not add or subtract eight hours on individual pages. Implement date-range start and end boundaries according to the specific query contract; do not assume every API uses the same inclusive or exclusive intervals.

## Shared error codes

The following table corresponds to backend `ResultCodes` and `ErrorCode`. When adding a shared code, update the backend definitions, client recognition, and this table together. Individual APIs should add their own triggering conditions instead of repeating the entire shared code table.

| code | error | HTTP | Meaning |
| --- | --- | --- | --- |
| `0` | `null` | 200 | Business operation succeeded |
| `40000` | `BAD_REQUEST` | 400 | Invalid request format or parameter type |
| `40010` | `INVALID_ARGUMENT` | 400 | Parameters fail validation rules |
| `40011` | `CAPTCHA_INVALID` | 400 | CAPTCHA is incorrect, expired, or already consumed |
| `40100` | `UNAUTHENTICATED` | 401 | Not logged in, invalid credentials, or invalidated session |
| `40300` | `FORBIDDEN` | 403 | No permission for the operation |
| `40301` | `CSRF_INVALID` | 403 | CSRF credential or request-origin validation failed |
| `40400` | `NOT_FOUND` | 404 | Resource does not exist or has been deleted |
| `40900` | `CONFLICT` | 409 | Unique constraint, relationship, or concurrent state conflict |
| `42900` | `RATE_LIMITED` | 429 | Request rate limit exceeded |
| `50000` | `INTERNAL_ERROR` | 500 | Unexpected internal exception |
| `50300` | `DEPENDENCY_UNAVAILABLE` | 503 | Required dependency temporarily unavailable |

Example error structure:

```json
{
  "code": 40300,
  "data": null,
  "error": "FORBIDDEN",
  "message": "无权执行该操作"
}
```

Use `code` or `error` for stable identification; `message` is for display. The frontend already provides error mapping and authentication handling. New pages must not decide whether to refresh, log out, or retry based on a particular Chinese message.

## Authentication and authorization

- Ordinary protected APIs include `Authorization: Bearer <accessToken>`, managed by the existing request layer.
- Refresh uses an HttpOnly Cookie; browser business code must not read that Cookie itself.
- Refresh and logout follow the CSRF flow: first obtain `data.token` from `/api/auth/csrf`, then send `X-XSRF-TOKEN` as in the existing implementation. Login, refresh, and logout are also subject to origin validation.
- The mandatory initial password change uses a dedicated password change token; it cannot access business APIs as a normal access token.
- APIs use Spring Security to check permissions, while Services also check target scope and built-in data protection.

For example, `system:user:list` controls reading the user list. Menu visibility and button visibility help users discover available actions; they cannot replace API permission checks. Maintain new permission codes and component mappings as described in [Menus and Permissions](/en/development/permissions).

## API documentation and changes

The purpose of a Controller, request fields, and necessary constraints should be understandable in OpenAPI. DTO/VO comments should explain more than the type name. Maintain shared response and error explanations centrally instead of attaching the same error table to every API.

Knife4j / OpenAPI is enabled by default only in development and test environments and disabled in production. Do not resolve debugging problems by relaxing permission or origin checks.

Before changing paths, required fields, types, error semantics, or authentication flows, check current frontend calls and tests. Update API types, related pages, and usage documentation together, and explain compatibility impacts.
