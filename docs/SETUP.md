# Setup

## 1. Get an API key

Create a key in [Google AI Studio](https://aistudio.google.com/apikey).

## 2. Store it as a GitHub Actions secret

In your repository:

**Settings → Secrets and variables → Actions → New repository secret**

Pick any name. Examples: `MY_AI_KEY`, `GEMINI_API_KEY`, `PROD_LLM_KEY`.

The Action never assumes the secret name. You map it to the `api-key` input.

## 3. Add the workflow

`.github/workflows/code-review-agent.yml`:

```yaml
name: Code Review Agent by boghus

on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]

permissions:
  contents: read
  pull-requests: write

concurrency:
  group: code-review-agent-by-boghus-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  review:
    runs-on: ubuntu-latest
    if: github.event.pull_request.head.repo.full_name == github.repository && github.event.pull_request.draft == false
    steps:
      - uses: actions/checkout@v5
        with:
          ref: ${{ github.event.pull_request.head.sha }}
          fetch-depth: 0

      - uses: boghus/code-review-agent@v1
        with:
          api-key: ${{ secrets.MY_AI_KEY }}
          model: gemini-2.5-flash
```

## 4. (Optional) Customize the review rules

Create `.github/code_review_rules.md` on the **base branch** with
project-specific guidance. It is treated as **trusted configuration** by
the reviewer.

🔐 **Important**: the rules file is read from the PR base ref, not from
the PR head. A contributor who modifies `.github/code_review_rules.md`
inside their PR will have those changes reviewed as part of the diff but
those changes will NOT influence the review of that same PR. This is
what makes the "trusted rules" label actually true.

## 5. Open a Pull Request

The Action will:

1. Build the diff against the base SHA.
2. Call Gemini with the prompt + rules.
3. Write the review to `cra-review.md`.
4. Locate the previous review comment (if any) by the marker
   `<!-- code-review-agent-by-boghus -->` (legacy comments from v1 with
   the marker `<!-- code-review-agent -->` are also picked up so they are
   replaced instead of duplicated).
5. Replace its body with the new review.

If the AI provider is unavailable, a failure comment is posted and the PR
is not blocked.

## Path resolution

`rules-path` must be a path **inside the repository** (relative to the
repo root, no leading slash). The action reads it from the PR base ref
via `git show <base>:<path>`, validates the entry is a regular blob,
and writes the bytes to a fresh `mktemp` file under `$RUNNER_TEMP`. The
absolute path of that temp file is what the orchestrator reads as
`CRA_RULES_PATH`. The workspace the PR controls is never the
destination — a symlink pre-created by the PR cannot redirect our
write.

`diff-path` and `output-path` are runner files, not part of the repo.
They accept either form:

- **Relative**: resolved against `${{ github.workspace }}` (the consumer's
  repo working directory).
- **Absolute** (starts with `/`): used as-is.

Gradle runs with the action repo as its working directory, not the
consumer repo. Without this resolution step, every relative path would
silently be looked up in the wrong directory. The resolution happens once
in the `Resolve path inputs` step and the absolute values are passed to
the orchestrator via `CRA_DIFF_PATH` and `CRA_OUTPUT_PATH`.

## Validating a release candidate

The E2E suite in `e2e/` runs the action against real pull requests opened
from fixture branches, and checks the published comment. It is the evidence
that a release candidate is ready.

### Running it locally

```bash
./e2e/run-suite                    # every scenario against main
./e2e/run-suite --scenario clean   # one scenario
./e2e/run-suite --list             # the scenarios and their branches
```

The suite needs the `gh` CLI authenticated with write access to the
repository, since it creates the fixture pull requests and pushes the empty
commits that trigger each run.

It exits with a non-zero status when a scenario does not pass, so it can gate
a release.

The assertions read the published comment, not the state of the workflow run.
A fixture branch always fails its QA merge guard by design, so the run status
carries no signal; the comment does.

### Validating a specific reference

To validate a release candidate, recreate the fixture branches from that
reference and run the suite against them:

```bash
./e2e/run-suite --action-ref v1.0.0-rc.1 --recreate-fixtures
```

The fixture branch is rebuilt from the reference under test, so the code the
reviewer executes is the code of that reference and the diff it reviews is
exactly the fixture change.

### Running it from Actions

`Release candidate validation` is a `workflow_dispatch` workflow that runs
the suite against a given reference:

```yaml
- uses: boghus/code-review-agent/.github/workflows/release-candidate.yml@main
  with:
    action_ref: v1.0.0-rc.1
    scenario: ''      # empty runs every scenario
    timeout: '480'
```

It uploads the published comments as artifacts, so a failure can be
diagnosed after the fact.

### Reading the result

Each scenario reports one of:

| Class | Meaning | Worth retrying |
|---|---|---|
| `action` | a structural defect: no comment, two comments, a malformed report | no |
| `llm_variance` | the report is well formed but does not contain the expected finding | no |
| `provider` | the action could not reach the provider | yes |

A provider outage is never a product defect, so it is retried once by
default. Model variance is never retried: a finding the model missed is a
real signal about the model, and retrying until it appears would hide what
the suite exists to measure.

The `medium` scenario depends on the model reporting a duplication as a
maintainability issue. Pure duplication in unused code is arguable, so that
fixture carries an observable consequence instead.
