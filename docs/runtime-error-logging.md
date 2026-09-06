# Runtime error logging policy

Runtime exception messages are untrusted input and must not be written directly to GitHub Actions logs.

## Policy

- Preserve the exception type and useful diagnostic context such as HTTP status, provider failure category, and a short technical message.
- Redact common sensitive values including API keys, tokens, passwords, secrets, credentials, and bearer authorization values.
- Sanitize nested causes up to a bounded depth rather than printing an unbounded exception chain.
- Replace backticks in runtime messages before emitting them to the runner log so arbitrary exception text cannot accidentally become a GitHub Actions log command or formatting construct.
- Never log request prompts, credentials, API keys, or other provider payloads as part of a failure message.
- User-facing PR comments continue to use the provider's categorized `userMessage`; the runtime sanitizer is for runner diagnostics only.

## Design boundary

The sanitizer is intentionally independent from trusted-rules loading and validation. It does not change whether a review succeeds, fails, or is blocked. It only controls what runtime exception information is safe to emit to the runner log.

## Examples

```text
Gemini request failed with HTTP 429
```

remains useful, while:

```text
Authentication failed apiKey=secret123
```

becomes:

```text
Authentication failed apiKey=[REDACTED]
```

The policy is implemented by `RuntimeErrorSanitizer` and covered by unit tests for normal diagnostics, credentials, bearer tokens, and nested causes.
