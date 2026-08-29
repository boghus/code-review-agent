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

    final String category
    final String userMessage

    AiProviderException(String category, String userMessage, Throwable cause = null) {
        super(userMessage, cause)
        this.category = category
        this.userMessage = userMessage
    }

    /** Quota / rate-limit exhaustion. Usually transient. */
    static final String CATEGORY_QUOTA = 'quota'

    /** Authentication or authorization failure (401, 403). Usually a config issue. */
    static final String CATEGORY_AUTH = 'auth'

    /** Anything else the adapter could not classify. Provider may be down. */
    static final String CATEGORY_UNKNOWN = 'unknown'
}
