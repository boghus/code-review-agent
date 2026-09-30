package com.boghus.codereview.review

import groovy.json.JsonOutput
import groovy.transform.CompileStatic

@CompileStatic
class ReviewResponseJsonSerializer {

    static String toJson(ReviewResponse response) {
        if (response == null) {
            throw new IllegalArgumentException('Review response must not be null.')
        }

        Map<String, Object> payload = [
            summary : response.summary,
            findings: response.findings.collect { ReviewFinding finding ->
                [
                    severity      : finding.severity.name(),
                    title         : finding.title,
                    file          : finding.file,
                    line          : finding.line,
                    description   : finding.description,
                    impact        : finding.impact,
                    recommendation: finding.recommendation
                ]
            }
        ]

        JsonOutput.toJson(payload)
    }
}
