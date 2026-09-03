package com.boghus.codereview.provider

import groovy.transform.CompileStatic

/**
 * Provider-agnostic categories for failures raised while invoking an AI provider.
 */
@CompileStatic
enum AiProviderErrorCategory {
    AUTHENTICATION,
    QUOTA,
    TIMEOUT,
    NETWORK,
    INVALID_REQUEST,
    UNKNOWN
}
