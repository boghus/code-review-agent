package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
class ReviewFinding {
    final ReviewSeverity severity
    final String title
    final String file
    final Integer line
    final String description
    final String impact
    final String recommendation

    ReviewFinding(
        ReviewSeverity severity,
        String title,
        String file,
        Integer line,
        String description,
        String impact,
        String recommendation
    ) {
        this.severity = requireValue(severity, 'severity')
        this.title = requireValue(title, 'title')
        this.file = file
        this.line = line
        this.description = requireValue(description, 'description')
        this.impact = impact
        this.recommendation = recommendation
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Review finding field '${fieldName}' must not be null.")
        }
        return value
    }
}
