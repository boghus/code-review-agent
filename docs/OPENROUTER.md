# OpenRouter MVP

OpenRouter is an optional provider for Code Review Agent. Gemini remains the default, so existing workflows do not change.

## Setup

1. Create an API key at https://openrouter.ai/keys.
2. Save it as a GitHub Actions secret, for example OPENROUTER_API_KEY.
3. Configure the action:

    - uses: boghus/code-review-agent@v1
      with:
        api-key: ${{ secrets.OPENROUTER_API_KEY }}
        provider: openrouter
        model: openrouter/free

The free router selects an available free model dynamically. Model availability and free-tier rate limits can change; this is suitable for an MVP and low-volume testing, not a service-level availability guarantee. See https://openrouter.ai/pricing and https://openrouter.ai/docs/guides/routing/routers/free-router.

The adapter uses the OpenAI-compatible Chat Completions endpoint and the JDK HTTP client, so it adds no provider SDK dependency. The API key is passed through the existing generic api-key input. The GitHub token remains separate and is not sent to OpenRouter.

## Error handling

- HTTP 401/403: authentication failure.
- HTTP 429: rate limit or quota reached.
- HTTP 400/404/422: invalid request or model identifier.
- HTTP 408/504: timeout.
- Other errors: provider failure.

The action remains non-blocking when provider calls fail. Free model selection is not deterministic; use a specific model ID ending in :free if you need to pin a model, after verifying it is currently available.
