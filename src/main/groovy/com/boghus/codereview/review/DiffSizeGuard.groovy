package com.boghus.codereview.review

import groovy.transform.CompileStatic

/**
 * Decides whether a diff is small enough to be sent to the model in full.
 *
 * Two independent thresholds: bytes and lines. Either one tripping is
 * enough to reject the diff. The thresholds exist so a single huge line
 * (think a regenerated minified asset) is rejected even when the line
 * count is tiny.
 *
 * If the diff is rejected the caller should post a warning comment instead
 * of asking the model to review an oversized context — large inputs are the
 * most common cause of 4xx errors and quota exhaustion in production.
 */
@CompileStatic
class DiffSizeGuard {

    static final int DEFAULT_MAX_BYTES = 200_000
    static final int DEFAULT_MAX_LINES = 4_000

    final int maxBytes
    final int maxLines

    DiffSizeGuard(int maxBytes, int maxLines) {
        this.maxBytes = maxBytes
        this.maxLines = maxLines
    }

    DiffSizeGuard() {
        this(DEFAULT_MAX_BYTES, DEFAULT_MAX_LINES)
    }

    DiffSizeDecision evaluate(String diff) {
        if (!diff) {
            return new DiffSizeDecision(true, 0, 0, null)
        }
        int bytes = diff.getBytes('UTF-8').length
        int newlineCount = diff.count('\n') as int
        int lines = newlineCount
        if (!diff.endsWith('\n')) {
            lines = newlineCount + 1
        }
        if (bytes > maxBytes) {
            return new DiffSizeDecision(false, bytes, lines,
                "Diff size is ${bytes} bytes which exceeds the configured limit of ${maxBytes} bytes.")
        }
        if (lines > maxLines) {
            return new DiffSizeDecision(false, bytes, lines,
                "Diff has ${lines} lines which exceeds the configured limit of ${maxLines} lines.")
        }
        return new DiffSizeDecision(true, bytes, lines, null)
    }

    static class DiffSizeDecision {
        final boolean acceptable
        final int bytes
        final int lines
        final String reason

        DiffSizeDecision(boolean acceptable, int bytes, int lines, String reason) {
            this.acceptable = acceptable
            this.bytes = bytes
            this.lines = lines
            this.reason = reason
        }
    }
}
