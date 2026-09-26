package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
class ReviewResult {

    final List<Finding> findings
    final boolean valid

    ReviewResult(List<Finding> findings = [], boolean valid = true) {
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings ?: []))
        this.valid = valid
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
