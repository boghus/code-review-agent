package com.boghus.codereview.provider

import groovy.transform.CompileStatic

/**
 * Provider-agnostic exception raised by {@link AiProvider} implementations.
 *
 * Adapters translate their own SDK-specific failures (HTTP status codes,
 * rate limits, auth errors, malformed responses) into this exception so
 * the orchestrator never has to know which provider raised what.
 *
 * <p>{@link #userMessage} is the comment-safe, human-readable description
 * of the failure. It is safe to render in a Pull Request comment and must
 * never contain raw provider internals such as API keys, account IDs or
 * stack traces.</p>
 */
@CompileStatic
class AiProviderException extends RuntimeException {

    final AiProviderErrorCategory category
    final String userMessage

    AiProviderException(AiProviderErrorCategory category, String userMessage, Throwable cause = null) {
        super(userMessage, cause)
        this.category = category
        this.userMessage = userMessage
    }
}
