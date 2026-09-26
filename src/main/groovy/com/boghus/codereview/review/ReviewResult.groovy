package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
class ReviewResult {

    final List<Finding> findings

    ReviewResult(List<Finding> findings = []) {
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings ?: []))
    }

    int count(String severity) {
        (int) findings.count { it.severity.equalsIgnoreCase(severity) }
    }

    boolean hasCriticalOrHigh() {
        count('CRITICAL') > 0 || count('HIGH') > 0
    }

    boolean isEmpty() {
        findings.isEmpty()
    }
}
