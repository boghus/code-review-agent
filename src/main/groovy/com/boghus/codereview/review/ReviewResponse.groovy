package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
class ReviewResponse {
    final String summary
    final List<ReviewFinding> findings

    ReviewResponse(String summary, List<ReviewFinding> findings) {
        this.summary = requireValue(summary, 'summary')
        this.findings = findings == null ? [] : List.copyOf(findings)
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Review response field '${fieldName}' must not be null.")
        }
        return value
    }
}
