# GitHub repository and branch-protection setup

The repository's current primary branch is `main`. Treat it as the branch referred to as
“master” in the project rules: direct pushes are forbidden.

## Local Git hook

The versioned `.githooks/pre-push` hook rejects direct updates and deletion of `main` and
`master`. Enable it in every local clone:

```bash
git config --local core.hooksPath .githooks
```

Do not bypass this check with `git push --no-verify`. A local hook protects only clones where it
is enabled and can be bypassed deliberately, so it does not replace server-side branch protection.

## CI

The workflow at `.github/workflows/ci.yml` runs on pull requests targeting `main`, pushes to
non-primary branches, and manual dispatch. Its required job is `Verify build and tests`.

The job uses Java 21 and the Gradle Wrapper to run:

```bash
./gradlew --no-daemon clean check
```

`check` must execute unit tests, integration tests, and JaCoCo coverage verification.
The workflow has read-only repository permissions and does not push commits or publish artifacts.

## Required one-time GitHub configuration

Branch protection cannot be enforced from a file in the repository. After creating the public
GitHub repository, create an active branch ruleset in **Settings → Rules → Rulesets**:

1. Target the default branch `main`.
2. Do not add a bypass actor, including repository administrators.
3. Require a pull request before merging and at least one approving review.
4. Dismiss stale approvals when new commits are pushed.
5. Require conversation resolution before merging.
6. Require the CI status check shown by GitHub for **CI / Verify build and tests**.
7. Require the branch to be up to date before merging.
8. Block force pushes and branch deletion.

After the first workflow run, select the exact CI check name that GitHub displays. This prevents
a pull request from being merged until the workflow succeeds.

If the default branch is renamed to `master`, change both the workflow trigger and the ruleset
target from `main` to `master` in the same pull request.
