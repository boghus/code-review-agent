package com.boghus.codereview.output

import groovy.transform.CompileStatic

/**
 * Writes the markdown that will become the PR comment body.
 *
 * <h3>Identity</h3>
 *
 * The agent publishes under the visible name {@code Code Review Agent by boghus}.
 * Bodies begin with {@link #COMMENT_MARKER}, which is the lookup key the
 * action uses to find the previous run's comment and replace it in place.
 *
 * <h3>Idempotency</h3>
 *
 * The marker is the contract between the writer and the composite action
 * steps that post the comment. Idempotency is guaranteed by the combination
 * of (a) every body carrying the marker and (b) the composite action steps
 * wiring it through {@code body-regex} on the find-comment step. If those
 * steps are ever replaced, the replacement must honour the marker contract
 * or every push will spawn a new comment.
 *
 * <h3>Compatibility with v1</h3>
 *
 * v1 used {@code <!-- code-review-agent -->}. {@link #LEGACY_MARKER} is
 * recognised by the action steps only as a search key, so existing comments
 * from v1 are picked up and replaced in place. New comments are always
 * tagged with {@link #COMMENT_MARKER}.
 */
@CompileStatic
class ReviewReportWriter {

    /** Marker written on every body. Used by the action to find and update the comment. */
    static final String COMMENT_MARKER = '<!-- code-review-agent-by-boghus -->'

    /** Legacy v1 marker. Accepted as a search key during the v1→v2 migration window. */
    static final String LEGACY_MARKER = '<!-- code-review-agent -->'

    private final String actionRef
    private final String actionSha

    ReviewReportWriter() {
        this(null, null)
    }

    ReviewReportWriter(String actionRef, String actionSha) {
        this.actionRef = actionRef?.trim()
        this.actionSha = actionSha?.trim()
    }

    void write(String outputPath, String body) {
        String version = actionRef ? "\n---\n🤖 Code Review Agent\nVersion: ${actionRef}\n" : ''
        String commit = actionSha ? "Commit: ${actionSha}\n" : ''
        new File(outputPath).write("${COMMENT_MARKER}\n${body}\n${version}${commit}", 'UTF-8')
    }

    void writeEmpty(String outputPath) {
        write(outputPath, """## 🤖 Code Review Agent by boghus

No changes to review.
""")
    }

    void writeFailure(String outputPath, String message) {
        write(outputPath, """## ⚠️ Code Review Agent by boghus unavailable

${message}

The automated review is non-blocking. Re-run the workflow when the AI provider is available again.
""")
    }

    void writeMisconfigured(String outputPath, String message) {
        write(outputPath, """## ⚠️ Code Review Agent by boghus is not configured

${message}

The review did not run because required configuration is missing. See the action documentation for setup steps.
""")
    }

    void writeTooLarge(String outputPath, String reason, int bytes, int lines, int maxBytes, int maxLines) {
        write(outputPath, """## ⚠️ Code Review Agent by boghus skipped (diff too large)

${reason}

- Diff size: **${bytes} bytes**, **${lines} lines**
- Configured limits: **${maxBytes} bytes**, **${maxLines} lines**

The automated review was skipped to avoid sending an oversized context to the AI provider, which typically results in 4xx errors or quota exhaustion. To re-enable, raise the limits via the action inputs (`max-diff-bytes`, `max-diff-lines`) or split the Pull Request into smaller changes.
""")
    }
}
