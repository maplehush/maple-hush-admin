# Menus and Permissions

Once the page and APIs exist, connect them to the existing menu and role system. This example places “Announcement Management” under “Content Management”.

## Align three configurations

| Configuration | Example value | What it determines |
| --- | --- | --- |
| Route URL | /content/notice | The address opened in the browser |
| Page component key | /notice/list | Which Vue file under views to load |
| Permission code | system:notice:list, etc. | Whether the current user can access the page or perform an operation |

The URL and component file path need not be identical: this example uses a URL under content, while the component is in `views/notice/list.vue`. Do not put the Java module name in component or add a .vue suffix.

## 1. Register the page component

Edit the existing file:

```text
backend/admin-system-biz/src/main/resources/menu-components.txt
```

Keep its existing contents and append this line:

```text
/notice/list
```

The backend `SysMenuInput` reads allowed component keys from this resource and validates them when menus are created or updated. Creating the Vue file without registering it causes menu saving to report an unregistered component.

The resource is read during application initialization. Reload backend resources and restart according to your setup. When running a Jar, rebuild it; editing source files and restarting an old Jar is insufficient. Do not create another list with the same filename in another module.

## 2. Create the Content Management catalog

Using an account with menu maintenance permission, open System Management → Menu Management and add a catalog:

| Form field | Value |
| --- | --- |
| Name name | Content |
| Title meta.title | Content Management |
| Type type | catalog |
| Path path | /content |
| Component component | Use BasicLayout for a root catalog (select the existing form option) |
| Permission code authCode | Leave empty; catalogs have no permission code |
| Status | Enabled |

Save the catalog before creating its child page. Until a child page exists, do not configure a redirect to a nonexistent page. name and path must not conflict with existing nodes.

## 3. Create the announcement page

Add a menu under Content Management:

| Form field | Value |
| --- | --- |
| Parent pid | ID of the Content Management catalog just created |
| Name name | NoticeManage |
| Title meta.title | Announcement Management |
| Type type | menu |
| Full path path | /content/notice |
| Component component | /notice/list |
| Permission code authCode | system:notice:list |
| Status | Enabled |

The management API accepts the full path; the backend converts it for storage based on the parent node. Do not shorten it to notice before using the UI or management API. `GET /api/menu/all` returns routes visible to the current user; it is different from the management data returned by `GET /api/system/menu/list`.

When calling the menu creation API directly, use the following request structure. Replace pid with the actual catalog ID; do not submit the placeholder text:

```json
{
  "pid": "REPLACE_WITH_CONTENT_MANAGEMENT_CATALOG_ID",
  "name": "NoticeManage",
  "type": "menu",
  "path": "/content/notice",
  "component": "/notice/list",
  "authCode": "system:notice:list",
  "status": 1,
  "meta": { "title": "Announcement Management", "order": 10 }
}
```

## 4. Add operation buttons

Add three button nodes under the announcement page:

| Name name | Title | authCode |
| --- | --- | --- |
| NoticeCreate | Create announcement | system:notice:create |
| NoticeUpdate | Edit announcement | system:notice:update |
| NoticeDelete | Delete announcement | system:notice:delete |

Button nodes register operation permissions only. They have no path/component and do not appear as pages in the left navigation. The page node supplies query permission, so do not create another node for the same query code.

Current validation requires lowercase `system:resource:action`. The four permissions must match exactly in menus, frontend can(), and backend @PreAuthorize. Do not use notice in one place and announcement in another.

## 5. Authorize an ordinary role

Using an account that can maintain roles and grant menus, select the following for an ordinary development role in Role Management:

- The Content Management catalog.
- The Announcement Management page, which supplies query permission.
- The create, edit, and delete buttons the role actually needs.

Assign this role to an ordinary test user. Changes to role menus and user roles affect the authorization version, and affected users may need to sign in again. Follow the application prompt; do not simulate permissions by modifying local accessCodes.

The built-in super administrator has special authorization capabilities. Use an ordinary account to check role behavior and button visibility. Create permission alone cannot support the list and edit detail flow in this example; grant the page's query permission as well.

## 6. Responsibilities of the frontend and backend

| Location | Responsibility |
| --- | --- |
| Dynamic menus / routes | Which business pages a user can enter |
| can(), CellOperation / VbenTableAction | Whether page operation controls are visible |
| Controller @PreAuthorize | Whether the request has permission to execute the API |
| Service / Mapper | Business rules, target data, and transactional consistency |

Hiding frontend buttons does not secure the API; backend checks are required. Similarly, an API permission does not automatically create a Vue page or register a menu.

`src/router/access.ts` fetches backend menus and maps them to `views/**/*.vue`. This example does not require adding the same business path again in static routes, which would create two sources of routes and permissions.

To switch menu titles with the selected language, add entries following the project's mapping in `src/router/menu-locale.ts`. Adding notice.json alone does not automatically localize arbitrary Chinese titles in the database.

Continue to [API Integration](./api-integration.md).
