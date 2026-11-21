package com.boghus.codereview.provider

import groovy.transform.CompileStatic

/**
 * Executes a review with the primary provider and optionally retries it with
 * a configured backup when the primary fails for a transient availability reason.
 */
@CompileStatic
class AiProviderFallback {

    static final Set<AiProviderErrorCategory> RETRYABLE_CATEGORIES = [
        AiProviderErrorCategory.QUOTA,
        AiProviderErrorCategory.TIMEOUT,
        AiProviderErrorCategory.NETWORK,
        AiProviderErrorCategory.UNAVAILABLE
    ] as Set<AiProviderErrorCategory>

    static ReviewResult review(AiProvider primary, AiProvider backup, ReviewRequest request) {
        if (primary == null) {
            throw new IllegalArgumentException('Primary AI provider must not be null.')
        }

        try {
            return new ReviewResult(primary, primary.review(request), false)
        } catch (AiProviderException primaryFailure) {
            if (backup == null || !shouldFallback(primaryFailure)) {
                throw primaryFailure
            }

            println "Code Review Agent: ${primary.type().configName} failed [${primaryFailure.category}], trying backup provider ${backup.type().configName}."
            try {
                return new ReviewResult(backup, backup.review(request), true)
            } catch (AiProviderException backupFailure) {
                backupFailure.addSuppressed(primaryFailure)
                throw backupFailure
            }
        }
    }

    static boolean shouldFallback(AiProviderException exception) {
        return exception != null && RETRYABLE_CATEGORIES.contains(exception.category)
    }

    @CompileStatic
    static class ReviewResult {
        final AiProvider provider
        final String response
        final boolean fallbackUsed

        ReviewResult(AiProvider provider, String response, boolean fallbackUsed) {
            this.provider = provider
            this.response = response
            this.fallbackUsed = fallbackUsed
        }
    }
}
