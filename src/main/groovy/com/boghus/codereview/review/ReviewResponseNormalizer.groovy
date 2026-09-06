package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
class ReviewResponseNormalizer {

    static final String REVIEW_HEADER = '## 🤖 Code Review Agent by boghus'

    static String normalize(String response) {
        if (!response) {
            return response
        }

        int headerIndex = response.indexOf(REVIEW_HEADER)
        if (headerIndex < 0) {
            throw new IllegalArgumentException(
                'The AI provider response does not contain the expected review header.'
            )
        }

        return response.substring(headerIndex).trim()
    }
}
