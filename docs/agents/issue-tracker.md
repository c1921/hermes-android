# Issue tracker: GitHub

Issues and PRDs for this repository live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multiline bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments with `jq` and fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments` with appropriate label and state filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`.
- **Apply or remove labels**: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- **Close an issue**: `gh issue close <number> --comment "..."`.

Infer the repository from `git remote -v`. The `gh` CLI does this automatically inside the clone.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares one number space across issues and pull requests. Resolve an ambiguous `#42` with `gh pr view 42`, then fall back to `gh issue view 42`.

## Skill operations

- When a skill says to publish to the issue tracker, create a GitHub issue.
- When a skill says to fetch the relevant ticket, run `gh issue view <number> --comments`.

## Wayfinding operations

- **Map**: one issue labelled `wayfinder:map`, containing Notes, Decisions-so-far, and Fog.
- **Child ticket**: a GitHub sub-issue linked to the map. If sub-issues are unavailable, add it to the map task list and begin the child body with `Part of #<map>`.
- **Blocking**: use GitHub native issue dependencies. If unavailable, begin the child body with `Blocked by: #<n>`.
- **Frontier**: select the first open, unassigned child in map order with no open blocker.
- **Claim**: `gh issue edit <number> --add-assignee @me`.
- **Resolve**: comment with the answer, close the child, then add its context pointer to the map's Decisions-so-far.
