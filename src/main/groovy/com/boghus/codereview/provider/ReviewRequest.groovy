package com.boghus.codereview.provider

import groovy.transform.CompileStatic

/**
 * Provider-agnostic representation of a review request.
 *
 * Instructions are trusted application content. Repository content is always
 * untrusted and must remain in the final user/prompt content channel.
 */
@CompileStatic
class ReviewRequest {
    final String systemInstructions
    final String developerInstructions
    final String prompt
    final String untrustedRepositoryContent

    ReviewRequest(
        String systemInstructions,
        String developerInstructions,
        String prompt,
        String untrustedRepositoryContent
    ) {
        this.systemInstructions = requireValue(systemInstructions, 'systemInstructions')
        this.developerInstructions = requireValue(developerInstructions, 'developerInstructions')
        this.prompt = requireValue(prompt, 'prompt')
        this.untrustedRepositoryContent = requireValue(untrustedRepositoryContent, 'untrustedRepositoryContent')
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Review request field '${fieldName}' must not be null.")
        }
        return value
    }
}
