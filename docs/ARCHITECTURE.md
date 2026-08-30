# Architecture

## Goal

A GitHub Action that posts a single, idempotent AI review per Pull Request,
with a provider-agnostic core so Gemini is the first adapter, not the contract.

## High-level flow

```
pull_request event
   │
   ▼
actions/checkout (PR HEAD)
   │
   ▼
action.yml steps
   ├── Build diff (git diff base..head)
   ├── setup-java + setup-gradle
   ├── gradle run  ──►  CodeReview.main()
   │                         │
   │                         ├── ActionInputs.fromEnv()
   │                         ├── DiffAnalyzer.parse()
   │                         ├── ReviewPromptBuilder.build()
   │                         ├── AiProviderFactory.create('gemini', apiKey, model)
   │                         ├── GeminiAdapter.review(prompt)
   │                         └── ReviewReportWriter.write(output)
   └── peter-evans/find-comment + create-or-update-comment
```

## Modules

| Module              | Responsibility                                        |
|---------------------|-------------------------------------------------------|
| `github`            | Read environment / typed inputs.                      |
| `review`            | Build prompt, analyse diff.                           |
| `provider`          | Provider abstraction + first concrete adapter.        |
| `output`            | Render the markdown body that becomes the comment.    |
| `CodeReview`        | Orchestrator. No business logic of its own.           |

## Contracts

- `AiProvider` — `String review(String prompt)`. Stateless.
- `ActionInputs` — only carries values from `CRA_*` env vars.
- `ReviewReportWriter` — every body starts with the marker
  `<!-- code-review-agent-by-boghus -->`; that marker is what
  `peter-evans/find-comment` keys on. The action's find step also accepts
  the legacy v1 marker `<!-- code-review-agent -->` so existing comments
  are replaced in place instead of duplicated.

## Why provider-agnostic

The Action's public inputs use generic names: `api-key`, `model`, `provider`.
Consumers can name their secret whatever they want and pass any model.
Adding OpenAI or Anthropic is one adapter + one registry entry.

## Why composite action with setup-java

- No Docker layer to maintain or publish.
- Gradle handles dependency caching via `gradle/actions/setup-gradle`.
- Toolchain pins JDK 25 — reproducible builds.
