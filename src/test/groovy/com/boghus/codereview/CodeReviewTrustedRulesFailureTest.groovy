package com.boghus.codereview

import com.boghus.codereview.output.ReviewReportWriter
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class CodeReviewTrustedRulesFailureTest {

    @Test
    void 'trusted rules failure is reported as non-blocking without exposing details'() {
        File output = File.createTempFile('cra-rules-failure-', '.md')
        output.delete()
        output.deleteOnExit()

        String internalMessage = "Git command failed: fatal: token=super-secret /runner/work/private-repo"
        new ReviewReportWriter().writeTrustedRulesFailure(
            output.absolutePath,
            'The trusted review rules could not be loaded from the pull request base revision.'
        )

        assertThat(output.text)
            .contains('trusted review rules could not be loaded')
            .contains('non-blocking')
            .doesNotContain('super-secret')
            .doesNotContain('/runner/work/private-repo')
            .doesNotContain(internalMessage)
    }

    @Test
    void 'trusted rules failure does not pretend that a review was generated'() {
        File output = File.createTempFile('cra-rules-failure-', '.md')
        output.delete()
        output.deleteOnExit()

        new ReviewReportWriter().writeTrustedRulesFailure(
            output.absolutePath,
            'The trusted review rules are invalid or do not point to a regular file in the pull request base revision.'
        )

        assertThat(output.text)
            .contains('Code Review Agent by boghus skipped')
            .contains('automated review was skipped')
            .doesNotContain('AI-generated review')
    }
}
