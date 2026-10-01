package com.boghus.codereview.review

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

@CompileStatic
class ReviewResponseJsonParser {

    static ReviewResponse parse(String json) {
        if (!json) {
            throw new IllegalArgumentException('Review response JSON must not be empty.')
        }

        Object parsed = new JsonSlurper().parseText(json)

        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException('Review response JSON must be an object.')
        }

        Map<String, Object> payload = (Map<String, Object>) parsed

        String summary = requiredString(payload, 'summary')
        List<ReviewFinding> findings = parseFindings(payload)

        new ReviewResponse(summary, findings)
    }

    private static List<ReviewFinding> parseFindings(Map<String, Object> payload) {
        if (!payload.containsKey('findings')) {
            throw new IllegalArgumentException(
                "Review response field 'findings' must be an array."
            )
        }

        Object value = payload.findings

        if (!(value instanceof List)) {
            throw new IllegalArgumentException(
                "Review response field 'findings' must be an array."
            )
        }

        ((List<Object>) value).collect { Object item ->
            parseFinding(item)
        }
    }

    private static ReviewFinding parseFinding(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException('Each review finding must be an object.')
        }

        Map<String, Object> finding = (Map<String, Object>) value

        new ReviewFinding(
            parseSeverity(finding),
            requiredString(finding, 'title'),
            optionalString(finding, 'file'),
            optionalInteger(finding, 'line'),
            requiredString(finding, 'description'),
            optionalString(finding, 'impact'),
            optionalString(finding, 'recommendation')
        )
    }

    private static ReviewSeverity parseSeverity(Map<String, Object> finding) {
        String value = requiredString(finding, 'severity')

        try {
            ReviewSeverity.valueOf(value)
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Invalid review finding severity: '" + value + "'.",
                exception
            )
        }
    }

    private static String requiredString(
        Map<String, Object> object,
        String fieldName
    ) {
        Object value = object[fieldName]

        if (!(value instanceof String) || !value) {
            throw new IllegalArgumentException(
                "Review response field '" + fieldName + "' must be a non-empty string."
            )
        }

        (String) value
    }

    private static String optionalString(
        Map<String, Object> object,
        String fieldName
    ) {
        Object value = object[fieldName]

        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException(
                "Review response field '" + fieldName + "' must be a string or null."
            )
        }

        (String) value
    }

    private static Integer optionalInteger(
        Map<String, Object> object,
        String fieldName
    ) {
        Object value = object[fieldName]

        if (value != null && !(value instanceof Integer)) {
            throw new IllegalArgumentException(
                "Review response field '" + fieldName + "' must be an integer or null."
            )
        }

        (Integer) value
    }
}
