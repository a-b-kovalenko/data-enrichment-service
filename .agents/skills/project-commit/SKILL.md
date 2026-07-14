---
name: project-commit
description: >-
  Validate, stage, commit, or push changes in this Java project using Gradle quality gates,
  Conventional Commits, and protected-branch workflow. Use only after explicit authorization;
  commit and push require separate explicit authorization.
---

# Project Commit Workflow

## Authorization

- Do not run `git add`, `git commit`, or `git push` unless the user explicitly requests the
  corresponding action.
- Treat “commit” as authorization to stage and create a commit only. Do not push after a commit
  unless the user explicitly requests a push.
- Treat “push” as authorization to push existing local commits only. Do not create a commit first
  unless the user also explicitly requests a commit.
- Never infer authorization from “finish”, “complete”, “save”, or successful checks.
- Never bypass the versioned `pre-push` hook with `--no-verify`.

## Validate Changes

1. Run `git status --short` and inspect the relevant `git diff` before staging.
2. Run `git diff --check` for whitespace errors.
3. After every completed logical Java, Gradle, configuration, or test change, run:

   ```bash
   ./gradlew --no-daemon clean build
   ```

   Resolve every failure before continuing. During diagnosis, run the smallest relevant Gradle task.
   Before a commit, additionally run:

   ```bash
   ./gradlew --no-daemon check
   ```

4. Report a failed or unavailable required check and request direction before committing.
5. Do not add unrelated user changes to the commit.

## Commit

1. Stage only files that belong to the requested change.
2. Use a Conventional Commit message:
   - `feat:` for a user-visible capability;
   - `fix:` for a defect;
   - `docs:` for documentation;
   - `style:` for formatting without behavior change;
   - `refactor:` for internal restructuring;
   - `test:` for tests only;
   - `build:` for Gradle, CI, or tool configuration;
   - `chore:` for maintenance.
3. Create the commit only after explicit user authorization.
4. Report the commit hash, branch, message, and changed files.

## Push

1. Confirm the current branch is neither `main` nor `master`.
2. Push only after explicit user authorization.
3. Let `.githooks/pre-push` run. It blocks direct updates and deletion of `main` and `master`.
4. Report the remote and branch after a successful push.
