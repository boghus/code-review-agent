package com.boghus.codereview.provider

/**
 * Provider-agnostic AI client. Implementations must be stateless and thread-safe.
 *
 * The contract is intentionally narrow: receive a prompt, return the raw
 * review text. Anything provider-specific (retries, JSON schemas, streaming,
 * token counting) lives inside the adapter.
 */
interface AiProvider {
    String name()

    String review(String prompt)
}
