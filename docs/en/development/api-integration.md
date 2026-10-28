# API Integration

After implementing the announcement example, follow this page to check that the APIs, pages, and authorization work together correctly.

## 1. Inspect the actual request flow

```text
list.vue / form.vue
  → src/api/notice.ts
  → requestClient (/api, Bearer, response unwrapping)
  → Vite proxy / frontend runtime entry
  → NoticeController → INoticeService → Mapper → MySQL
```

See [Getting Started](/en/getting-started/) for starting base services and configuring the proxy. After adding backend modules, Java code, and resources, rebuild and start the actual application. During frontend development, Vite loads the pages.

## 2. View new APIs in the existing API documentation

By default, the development environment serves Knife4j at `http://127.0.0.1:8080/doc.html` and OpenAPI at `http://127.0.0.1:8080/v3/api-docs`. Use the port from your actual backend configuration.

With @RestController, @RequestMapping, and @Tag on NoticeController, it is scanned once it is on the application's runtime classpath. Do not create a separate manual Swagger JSON file. If the APIs are missing, check startup module dependencies and the package scanning scope first.

Protected APIs require the access token obtained by signing in as yourself. Enter the access token in BearerAuth; the documentation tool constructs the Bearer header. AUTH_JWT_SECRET is not a login token. When debugging graphical CAPTCHA, first password change, refresh, and logout, follow the existing authentication flow and keep permission, CSRF, and Origin checks enabled.

API documentation is enabled only in dev/test by default. Do not temporarily expose documentation in production to perform testing.

## 3. Check pagination and write results

Creation request body:

```json
{
  "title": "Welcome to Announcement Management",
  "content": "This is an announcement for the teaching example.",
  "status": 1
}
```

Expected creation response shape:

```json
{
  "code": 0,
  "data": { "id": "1" },
  "error": null,
  "message": "ok"
}
```

The ID 1 is illustrative; use the value actually returned by your creation request for subsequent requests. The data shape of `GET /api/notice/list?page=1&pageSize=20` is:

```json
{
  "items": [
    {
      "id": "1",
      "title": "Welcome to Announcement Management",
      "content": "This is an announcement for the teaching example.",
      "status": 1,
      "createTime": "2026-10-28 10:00:00",
      "updateTime": "2026-10-28 10:00:00"
    }
  ],
  "total": 1
}
```

The page query function already receives this data. Do not wrap it in `{ data: ... }` when returning it to Grid. Successful updates and deletions return null data; refresh the list to display the result instead of waiting for an unspecified updated field.

## 4. Diagnose issues in the browser

Open Network in developer tools and inspect the actual request URL, method, status, body, and response:

| Symptom | Check first |
| --- | --- |
| Request becomes /api/api/notice | Whether the API wrapper adds /api twice |
| Response contains HTML or a Vite page | Request URL, development proxy target, and actual backend port |
| 404 or announcement APIs are missing | Whether the running Jar contains the new biz module and Controller mappings match |
| HTTP 401 | Current access token, session state, and whether signing in again is required |
| HTTP 403 | Actual account permissions; for Cookie-related authentication requests, also check Origin and CSRF |
| Saving a menu reports an unregistered component | menu-components.txt, the component key, and whether new resources have loaded |
| Page exists but the menu is missing | Node status, parent, role grants, and routes visible to the current user |
| List is empty despite a successful API response | data.items / data.total shape, filters, and the actual connected database |
| Content is cleared after editing | Whether details were fetched, submission is blocked after loading fails, and the DTO contains the complete form |
| Java ID becomes an inaccurate number | HTTP VO, frontend types, and accidental Number(id) conversion |

HTTP 200 alone does not establish success. Standard business responses must also contain code=0 and the agreed data. The existing refresh API has its own contract; do not apply its response format to these announcement APIs.

## 5. Completion checklist

The developer extending the project must perform these checks in their own development environment. They are not automated acceptance results for this documentation site.

### Basic operations

- [ ] Create an announcement with a title, content, and status, then fetch details using the actual returned ID.
- [ ] Queries support page number and page size, returning items/total in a stable order.
- [ ] After editing, the list and reopened details show the new values.
- [ ] After deletion, the list refreshes and querying the original ID returns NOT_FOUND.
- [ ] Writes generate audit records with the corresponding operation codes, without announcement content in log summaries.

### Parameters and permissions

- [ ] Empty titles, overlong titles/content, and invalid statuses are rejected.
- [ ] page=0, pageSize=101, nondecimal IDs, and IDs outside the Long range are rejected.
- [ ] Unauthenticated users cannot read announcement APIs.
- [ ] Users with query permission only can view the list but cannot create, edit, or delete; direct API calls must also be rejected.
- [ ] Menus and buttons work after granting an ordinary role; sign in again as prompted after grants change.
- [ ] Table headers, forms, and buttons are correct after switching between Chinese and English, and plain text content is not executed as HTML.

## 6. Code checks and ongoing maintenance

Run type and formatting checks in frontend using project commands, and compile and verify backend following the project workflow. Cover the modules and pages added in this change.

Maintain the new module POMs, migration version, API descriptions, page component allowlist, menu grants, and locale entries together. Use new migrations to upgrade existing schemas; do not delete historical databases or modify old scripts to “repeat the tutorial”.

This chapter provides a teaching reference implementation. Actual integration still requires building and testing in your development environment according to your business needs. A visible page alone does not establish feature completion.
