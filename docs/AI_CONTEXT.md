# AI Context

This document is the machine-readable context for AI coding and review agents working on `code-review-agent`.

## Purpose

Use this file to understand architectural boundaries before proposing or reviewing changes. It describes **why** components exist and which responsibilities must remain separated.

## Core rule: inputs have two responsibilities

GitHub Action inputs arrive as strings through `CRA_*` environment variables. The project separates:

```text
GitHub Actions environment
        |
        v
ActionInputs
(read/adapt)
        |
        v
InputParser
(parse/validate)
        |
        v
typed application configuration
```

### `ActionInputs`

`com.boghus.codereview.github.ActionInputs` is an adapter from the GitHub Actions environment to the application's typed configuration.

It should:

- read `CRA_*` variables;
- apply simple defaults that are part of the Action contract;
- delegate parsing/validation rules to `InputParser`;
- expose typed values to the orchestrator.

It should **not** accumulate parsing algorithms or validation rules for individual input types.

### `InputParser`

`com.boghus.codereview.github.InputParser` owns parsing rules that have meaningful semantics or validation.

Current contract:

- `parsePositiveInt(String raw, int fallback)` trims the input;
- blank input returns `fallback`;
- a valid integer greater than zero is returned;
- zero and negative values return `fallback`;
- malformed or out-of-range integer values return `fallback`;
- the helper must not exist merely as a wrapper around `String.toInteger()` / `Integer.parseInt()`; it must own the associated validation/semantic rule.

Do not add speculative helpers such as `parseEnum` or `parseBoolean` until there is a real consumer and a defined validation contract.

## Compatibility rule

When refactoring input parsing, preserve the existing observable behavior unless an issue explicitly requests a behavior change.

For optional numeric configuration, invalid values currently fall back to the configured default rather than failing the whole Action. A refactor must not silently turn these cases into exceptions.

## Testing expectations

Changes to parsing should include focused unit tests for the parser itself and integration-level coverage through `ActionInputs` where delegation matters.

For `parsePositiveInt`, cover at least:

- valid positive integer;
- surrounding whitespace;
- null/blank value;
- zero;
- negative value;
- malformed value;
- integer boundary/overflow.

## Architectural boundaries

- `CodeReview` orchestrates; it should not contain input parsing logic.
- `github` adapts GitHub-specific inputs.
- `review` analyses diffs and builds review prompts.
- `provider` owns the AI provider abstraction and adapters.
- `output` renders the final review body.

Prefer the smallest change that preserves these boundaries. Avoid introducing abstractions without a current use case.

## AI review guidance

When reviewing a change in this area, flag:

1. parsing logic added back into `ActionInputs`;
2. duplicated validation between `ActionInputs` and `InputParser`;
3. behavior changes to fallback/default handling without explicit requirements;
4. helpers that only delegate to standard library parsing without adding semantic validation;
5. tests that cover only the happy path;
6. documentation that describes an intended architecture but no longer matches the implementation.
