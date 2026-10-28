# Frontend Standards

The only current target application is `frontend/apps/web-antdv-next`. Keep the entire frontend workspace for dependency installation. Do not copy only the application directory or treat `backend-mock` as the real service.

## Directories and naming

| Location (relative to the target application) | Contents |
| --- | --- |
| `src/api/` | Request parameters, response types, and API functions |
| `src/views/<business>/list.vue` | List layout, queries, and action entry points |
| `src/views/<business>/data.ts` | Form schemas, column definitions, and data conversion |
| `src/views/<business>/modules/` | Local components such as drawers and dialogs |
| `src/adapter/` | Component contract adapters between the target UI library and Vben |
| `src/locales/langs/` | Language resources such as Chinese and English |

Directories and business files follow the existing lowercase, hyphenated style. Components, types, and variables follow conventions in the same directory. Prefer the existing `list.vue`, `data.ts`, and `modules/form.vue` organization instead of putting requests, form schemas, and every dialog into one page.

## Reusing components

For management pages, prefer existing `Page`, `useVbenVxeGrid`, `useVbenForm`, `useVbenDrawer` / `useVbenModal`, `VbenTableAction`, and `VbenDescriptions`. Use underlying input controls through the current AntdvNext adapter.

Do not build another basic Antdv Table/Form/Modal structure for lists, forms, and dialogs already wrapped by the project. When new capabilities are needed, first check whether the current wrapper supports them through configuration before extending the adapter.

See [Adding a Frontend Page](/en/development/new-page) for a complete page example.

## APIs and types

Ordinary business requests consistently use `requestClient` from `#/api/request`. It handles the `/api` prefix, Bearer authentication, response unwrapping, and authentication refresh. Pages do not duplicate this logic.

The following is taken from the role API:

```ts
import { requestClient } from '#/api/request';

export const getRole = (id: string) =>
  requestClient.get<SystemRole>(`/system/role/${id}`);
```

`SystemRole` is declared in the same API file. `getRole()` returns the unwrapped business object, so callers do not access `result.data.data`; the path must not repeat the prefix as `/api/system/role/...` either.

The authentication module has dedicated transport logic for exceptions such as refresh. New business pages must not bypass it by creating another login or token storage scheme.

- Declare IDs as `string`; do not convert them with `Number()`, `parseInt()`, or unary `+`.
- Use explicit types such as `0 | 1` for finite values like status; do not mix them with strings or booleans.
- Model query, save, and list responses separately. Do not substitute `any` or database entity shapes for the API contract.
- Select only writable fields when constructing save parameters; do not submit an entire list record through `{ ...row }`.

Example construction of role save parameters:

```ts
const payload: RoleSave = {
  name: values.name,
  status: values.status,
  remark: values.remark,
};
```

Whether optional fields are omitted, or cleared with `null` or an empty string, depends on the specific API convention. Do not make a global assumption.

## Form adapters

Use registered controls directly in the `component` and `componentProps` of a `useVbenForm` schema. Do not use `Input` as a placeholder and manually build an already supported control through a slot.

Current model bindings come from `src/adapter/form.ts`:

| Control | Model property |
| --- | --- |
| Default controls | `value` |
| Checkbox, Radio, Switch | `checked` |
| RichEditor and Upload wrappers | `modelValue` |

When adding a control, check component registration, type mappings, and the model contract together. Uploads use the existing Upload wrapper, connecting upload logic through `componentProps` / `customRequest`. Do not assume that the wrapper exposes every event from its underlying native component unchanged.

Business contracts determine whether a field is required. Do not make an optional field required just to display an asterisk. For example, roles are optional when adding a user; handle an empty role collection according to the current user API.

## Drawers and asynchronous operations

1. Reset previous form values, target IDs, and error state whenever a drawer opens.
2. Read details when editing as needed; avoid directly filling the form with incomplete list data.
3. If loading fails, retain a clear message and prevent submission of incomplete data.
4. Lock interaction during submission and unlock in `finally` to prevent duplicate requests or permanent loading.
5. Close the dialog and notify the list to refresh on success; retain input for correction on failure.

Reuse the existing deletion confirmation in `CellOperation` / `VbenTableAction` to avoid two confirmation dialogs from a single click. Coordinate error messages with the request layer so one failure does not produce multiple duplicate toasts.

## Internationalization and interface text

Use `$t` from `#/locales` for page titles, fields, actions, validation, and error text. Maintain both Chinese and English resources. New files follow the current `src/locales/langs/zh-CN/` and `en-US/` organization.

Regenerate columns and schemas that must update when the language changes within the current reactive flow or factory functions. Avoid permanently caching translated values at module load time.

```ts
label: $t('system.role.roleName')
```

Describe actions and fixable problems in terms users understand. Put implementation explanations about DTOs, permission codes, and caching mechanisms in development documentation instead of ordinary form hints.

## Permissions and public configuration

Menus, routes, and buttons use effective permissions returned by the backend. See [Menus and Permissions](/en/development/permissions) for integration. Hidden buttons cannot replace server validation; new route components must satisfy the backend component allowlist and actual frontend file mapping.

Browsers can read `VITE_` variables and frontend build artifacts. Store only public configuration in them, never database/Redis passwords, JWT signing keys, or OSS AccessKeys. Do not store login passwords either.

## Formatting and checks

The current project formats with Oxfmt and checks with ESLint, Oxlint, and Stylelint; do not maintain it using Prettier configuration. Vue, ESLint, and Oxc extensions are recommended for editors. Use the respective existing `.vscode` configuration when opening either the repository root or frontend to ensure the correct working directory.

Follow `frontend/.editorconfig`: UTF-8, LF, two spaces, and the other settings. Let configuration handle formatting and import ordering. Formatting on save and ESLint fixes are separate operations; save manually and inspect changes when needed.

After business changes, run the applicable checks in **`frontend/`**:

```sh
pnpm exec eslint apps/web-antdv-next/src --quiet
pnpm exec oxfmt --check apps/web-antdv-next/src
pnpm --filter @vben/web-antdv-next typecheck
pnpm exec vitest run apps/web-antdv-next/src --dom
pnpm --filter @vben/web-antdv-next build
```

When modifying shared packages, also check affected consumers. Review the diff after automatic fixes; successful formatting does not establish that business functionality has been validated.
