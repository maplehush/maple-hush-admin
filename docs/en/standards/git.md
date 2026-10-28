# Git Collaboration

One repository manages frontend, backend, docs, and deployment configuration. Use `develop` for daily development; `main` holds accepted stable code.

## Branch responsibilities

| Branch | Purpose |
| --- | --- |
| `develop` | Daily feature integration and development validation |
| `main` | Accepted stable versions and release source |
| Feature branch | Created from develop for one clear feature or fix |

Feature branches may be named `feat/notice`, `fix/login-error`, or `docs/api-rules`. Review completed features before merging into develop; merge into main after milestone acceptance. The two long-lived branches may differ during development. Do not skip acceptance merely to keep them identical.

Run the following commands from the **repository root**. They demonstrate a typical new-feature workflow; inspect local changes before running them:

```sh
git status
git switch develop
git pull --ff-only
git switch -c feat/notice
```

## Updating code and conflicts

`git pull --ff-only` permits only a fast-forward update to the **current branch**, usually fetching from that branch's configured upstream. It does not mean pulling only main, and it does not automatically update every local branch.

A fast-forward means the local branch has no divergent commits, so the branch pointer can move directly forward. If the histories have diverged, the command stops so you can inspect local and remote commits; it does not automatically create a merge.

To specify the source explicitly, for example when currently on develop:

```sh
git pull --ff-only origin develop
```

When conflicts occur, read both changes and consider related API and migration impacts, then check again after resolving them. Do not substitute force pushes, whole-file overwrites, or workspace cleanup for understanding conflicts. History changes to shared branches require team coordination.

## Commit contents and messages

Each commit expresses one clear purpose. Business changes may be committed together with their tests, migrations, and documentation. Handle unrelated formatting or refactoring separately.

Use `type: description` or `type(scope): description`, for example:

```text
feat(user): support optional role assignment
fix(auth): clarify login failure handling
docs: add development standards
```

Common types are `feat`, `fix`, `docs`, `refactor`, `test`, and `chore`; the existing commitlint configuration determines the actual allowed values. Describe what changed, and add the reason and compatibility impacts in the commit body when needed.

Select files, inspect staged changes, then commit. For example, when changing only one document:

```sh
git add docs/standards/api.md
git diff --cached
git commit -m "docs: clarify API response conventions"
```

Avoid committing every file without understanding the workspace contents, especially configuration, generated artifacts, or someone else's unfinished changes.

## What not to commit

- Real environment `.env` files, passwords, JWT signing keys, or OSS AccessKeys.
- `secrets/`, certificate private keys, backups, or debug logs containing sensitive values.
- `node_modules/`, `dist/`, `target/`, documentation site caches, or build artifacts.
- Personal editor configuration unrelated to the project or temporary workspace files.

The root `.gitignore` preserves `.env.example` and some public configuration files for the target frontend application. Permission to track a file does not permit secrets in it; frontend variables must be safe to publish.

The sample local `dev` database and Redis defaults are explicit development conventions retained by the project. Do not copy them into test or production credentials. If real secrets are accidentally committed, revoke or rotate them; deleting a working file does not erase Git history.

## Current automated checks

The current `.github/workflows/ci.yml` runs the following checks on pushes and pull requests for main and develop:

| Project | Current checks |
| --- | --- |
| frontend | Lockfile-based installation, ESLint, Oxfmt, target application type checking, Vitest, target application build |
| backend | Maven Wrapper `verify` under Java 21 |

The frontend also provides `lefthook.yml`: pre-commit checks include Oxlint, Oxfmt, ESLint, Stylelint, and type checking; commit-msg uses commitlint. Whether hooks run depends on local installation and activation. Do not assume they are active on every machine that clones the repository. Automatic fixes may change staged files; inspect the results before committing.

**Current CI performs code checks.** It does not deliver automatic test-environment deployment, production approval releases, or image promotion, and has no separate docs build job. It therefore does not establish that mandatory branch protection or a complete release pipeline is configured.

## Before merging

A commit or merge request description should include at least:

1. The purpose and main impacts of the change.
2. Whether APIs, databases, permissions, or configuration changed.
3. Commands actually run, their results, and anything still unverified.
4. Whether corresponding documentation was updated when usage changed.

After code checks pass, review behavior according to the change scope. Documentation changes require checking the documentation site and content; do not run additional business services just to fill a commit description. Business changes require the relevant validation described in the frontend and backend standards.
