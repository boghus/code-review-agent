# Code Review Agent by boghus

AI-powered Pull Request reviewer. Runs entirely inside GitHub Actions. Posts
a single, idempotent review comment per PR.

The Action is provider-agnostic. **Gemini** is the first supported provider;
the architecture is open to OpenAI, Anthropic and others.

## Why

- One review comment per PR, updated on every push (no spam).
- Provider-agnostic interface, easy to evolve.
- Runs inside your GitHub Actions runner. The PR diff and repository review rules are sent to the configured AI provider for analysis.
- Minimal input surface: `api-key` + `model` + optional `language`.

## Data & privacy

The action runs inside your GitHub Actions runner, but it is **not a
purely local tool**. To produce a review it sends the following to the
configured AI provider (`provider` input, default `gemini`):

- The full PR diff (every added/removed line).
- The contents of `rules-path` from the PR base ref.

Nothing else from your repository is sent. The model output is written
back into the runner and posted as a PR comment using your GitHub token.

Do not enable this action on repositories, files or branches whose
content is not allowed to be shared with the AI provider you configure.
Each provider has its own data-handling terms — review them before
turning the action on.

## Quickstart

Create `.github/workflows/code-review-agent.yml`:

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
          provider: gemini
          language: es
```

## GitHub permissions and token isolation

The action requires only these GitHub permissions in the consumer workflow:

```yaml
permissions:
  contents: read
  pull-requests: write
```

`contents: write`, `issues: write`, `actions: write` and other write
permissions are not required by the current implementation.

The `github-token` input is used exclusively by the GitHub comment steps
(`peter-evans/find-comment` and `peter-evans/create-or-update-comment`).
It is **not exposed to the Gradle/JVM process or any AI provider**. The
AI step explicitly clears `GITHUB_TOKEN` before starting Gradle and only
passes provider configuration through `CRA_*` variables.

The security invariant is:

```text
GITHUB_TOKEN ∉ Gradle environment
GITHUB_TOKEN ∉ AI provider
GITHUB_TOKEN ∉ review prompt

CRA_API_KEY → Gradle → AiProvider → configured AI provider
```

A CI security check verifies this boundary so a future provider or workflow
change cannot accidentally reintroduce the GitHub token into the AI process.

## Setup

1. Create an API key in [Google AI Studio](https://aistudio.google.com/apikey).
2. In your repo go to **Settings → Secrets and variables → Actions → New repository secret**.
3. Name it anything (e.g. `MY_AI_KEY`, `GEMINI_API_KEY`).
4. Map that secret to the `api-key` input as shown above.

The Action never assumes the secret name. You own that contract.

## Inputs

| Input        | Required | Default                | Description |
|--------------|----------|------------------------|-------------|
| `api-key`    | yes      | —                      | Provider API key. Map any repository secret. |
| `model`      | no       | `gemini-2.5-flash`     | Model identifier passed to the provider. |
| `provider`   | no       | `gemini`               | Provider implementation. Only `gemini` is wired in v1. |
| `language`   | no       | `en`                   | Review language. Supported values: `en`, `es`. Invalid values default to `en`. |
| `rules-path` | no       | `.github/code_review_rules.md` | Path of the rules file **inside the repository** (relative to the repo root). Read from the PR base ref. |
| `diff-path`  | no       | `cra-pr.diff`          | Where the PR diff is written. Relative to `${{ github.workspace }}` or absolute. |
| `output-path`| no       | `cra-review.md`        | Where the generated review is written. Relative to `${{ github.workspace }}` or absolute. |
| `github-token`| no      | `${{ github.token }}`  | Token used to post the comment. |
| `max-diff-bytes`| no    | `200000`               | Skip the review with a warning if the diff exceeds this many bytes. |
| `max-diff-lines`| no    | `4000`                 | Skip the review with a warning if the diff exceeds this number of lines. |

The review is generated directly in the selected language in the same AI
request. The action does not perform a second translation step.

### Path inputs

`rules-path` must be a path **inside the repository** (relative to the
repo root, no leading slash). It is read from the PR base ref via
`git show <base>:<path>`, which only accepts paths that exist in the
git tree. The bytes are written into a runner-controlled temp file
under `$RUNNER_TEMP` (never into the workspace the PR controls) and that
temp path is passed to the orchestrator as `CRA_RULES_PATH`. Even if the
PR pre-creates a symlink at the same location under the workspace, the
trusted content lives elsewhere and the orchestrator only reads the
file the action just wrote.

`diff-path` and `output-path` are runner files, not part of the repo.
They accept either form:

- **Relative**: resolved against `${{ github.workspace }}` (the consumer's
  repo working directory).
- **Absolute** (starts with `/`): used as-is.

Inside the action the resolved values are passed to Gradle as
`CRA_DIFF_PATH` and `CRA_OUTPUT_PATH`.

## Repository rules

Drop a markdown file at `.github/code_review_rules.md` (default). It is
**trusted configuration**: the reviewer treats its contents as instructions,
not as data. Keep it short, factual and free of secrets — never put prompts
that try to bypass the action's own system instructions in there.

⚠️ **Trust boundary**: the rules file is read from the **PR base ref**
(the branch the PR targets), not from the PR head. A contributor cannot
override the review contract by modifying the rules file inside their own
PR. If a PR adds or modifies `.github/code_review_rules.md`, those changes
are reviewed as part of the diff but do **not** influence the review of
that same PR.

## Architecture

```
com.boghus.codereview
├── CodeReview                  orchestrator
├── github
│   └── ActionInputs            env → typed config
├── provider
│   ├── AiProvider              contract
│   ├── AiProviderFactory       registry
│   └── GeminiAdapter           first implementation
├── review
│   ├── DiffAnalyzer            extracts changed files / lines
│   ├── ReviewLanguage           supported review languages
│   └── ReviewPromptBuilder     builds the prompt safely
└── output
    └── ReviewReportWriter      writes the PR comment body
```

Posting the comment is delegated to `peter-evans/find-comment` +
`peter-evans/create-or-update-comment`, which gives us idempotency
("update the existing comment" instead of "append a new one").

## Idempotency

Every output begins with `<!-- code-review-agent-by-boghus -->`. The
`peter-evans/find-comment` step uses that marker (and, for compatibility
with v1, also the older `<!-- code-review-agent -->` marker) to locate the
previous review; `peter-evans/create-or-update-comment` then replaces its
body in place. Re-running the workflow on the same PR updates the same
comment.

The marker alone does nothing — idempotency is guaranteed by the
combination of (a) every body carrying the marker and (b) the composite
action steps wiring it through `body-regex` on `peter-evans/find-comment@v4`.
If those steps are ever replaced, the replacement must honour the marker
contract or every push will spawn a new comment.

## Resiliency

- HTTP 408, 429, 500, 502, 503, 504 are retried 3 times by the SDK.
- 60 second request timeout.
- If the AI provider remains unavailable, the Action writes a failure
  comment and exits 0 — the PR is never blocked.
- Re-run the workflow from the Actions UI when the provider recovers.

## Building locally

```bash
gradle build
gradle test
```

To exercise the full pipeline locally, run the same steps the action runs:

```bash
# 1. Pick a base/head (here: HEAD vs HEAD~1) and produce a diff
git diff --unified=80 HEAD~1 HEAD > /tmp/cra-pr.diff

# 2. Invoke the Groovy orchestrator with absolute paths
export CRA_API_KEY=...
export CRA_DIFF_PATH=/tmp/cra-pr.diff
export CRA_OUTPUT_PATH=/tmp/cra-review.md
export CRA_RULES_PATH=$PWD/.github/code_review_rules.md
export CRA_LANGUAGE=es
gradle run --quiet

# 3. Inspect the generated review
cat /tmp/cra-review.md
```

Inside a workflow the action passes absolute paths under
`${{ github.workspace }}` because Gradle runs with the action repo as its
working directory, not the consumer repo.

## Adding a provider

1. Implement `AiProvider` (single method: `String review(String prompt)`).
2. Register it in `AiProviderFactory.REGISTRY`.
3. Add an `inputs.<provider>-api-key` only if the provider needs a
   different secret name; the generic `api-key` stays the contract.

## Roadmap

- Output formats (`markdown`, `sarif`).
- Custom rules path / inline rules.
- OpenAI and Anthropic adapters.
