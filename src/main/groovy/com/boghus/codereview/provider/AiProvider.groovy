package com.boghus.codereview.provider

/**
 * Provider-agnostic AI client. Implementations must be stateless and thread-safe.
 *
 * The core keeps trusted instructions separate from untrusted repository
 * content. Adapters map those channels to the strongest instruction mechanism
 * supported by their provider/model.
 */
interface AiProvider {
    AiProviderType type()

    AiProviderCapabilities capabilities()

    String review(ReviewRequest request)
}
