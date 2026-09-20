package com.boghus.codereview.coverage

import groovy.transform.CompileStatic

@CompileStatic
class DiffCoverageResult {
    final DiffCoverageStatus status
    final int changedLines
    final int executableLines
    final int coveredLines
    final int missedLines
    final List<ChangedSourceLine> covered
    final List<ChangedSourceLine> missed
    final String message

    DiffCoverageResult(
        DiffCoverageStatus status,
        int changedLines,
        int executableLines,
        int coveredLines,
        int missedLines,
        List<ChangedSourceLine> covered,
        List<ChangedSourceLine> missed,
        String message
    ) {
        this.status = status
        this.changedLines = changedLines
        this.executableLines = executableLines
        this.coveredLines = coveredLines
        this.missedLines = missed
        this.covered = Collections.unmodifiableList(new ArrayList<>(covered))
        this.missed = Collections.unmodifiableList(new ArrayList<>(missed))
        this.message = message
    }

    BigDecimal coveragePercentage() {
        if (executableLines == 0) {
            return 0G
        }
        return (coveredLines * 100G).divide(executableLines as BigDecimal, 2, BigDecimal.ROUND_HALF_UP)
    }
}
