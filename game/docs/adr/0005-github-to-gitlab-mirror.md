# ADR 0005 - Mirror GitHub to GitLab via GitHub Action

## Context

The project's CI (`.gitlab-ci.yml`) runs on GitLab and deploys the game to GitLab
Pages, but development happens on GitHub (`tyrcho/penta`). GitLab previously pulled
from the GitHub mirror automatically; GitLab has since stopped offering repository
mirroring (pull or push) for free on its free tier, so `gitlab.com/tyrcho/penta` was
going stale and its Pages deploy no longer reflected new GitHub commits.

## Decision

Added `.github/workflows/mirror-to-gitlab.yml`: on every branch or tag push to
GitHub (plus manual `workflow_dispatch`), it does a full `git clone --mirror` of the
GitHub repo and `git push --mirror`s it to `gitlab.com/tyrcho/penta`, authenticated
with a `GITLAB_TOKEN` repo secret (a GitLab access token with `write_repository`
scope). This moves the mirroring responsibility from GitLab (pull, no longer free)
to GitHub (push, free on GitHub Actions), with no change to `.gitlab-ci.yml` or the
Pages deploy itself — GitLab just sees pushes arrive from the Action instead of
pulling them on its own schedule.
