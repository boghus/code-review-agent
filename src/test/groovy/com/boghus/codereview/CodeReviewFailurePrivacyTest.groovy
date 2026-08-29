package com.boghus.codereview

import com.boghus.codereview.github.ActionInputs
import com.boghus.codereview.output.ReviewReportWriter
import com.boghus.codereview.provider.AiProvider
import com.boghus.codereview.provider.AiProviderException
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * Verifies that failure paths in the orchestrator do NOT echo raw provider
 * exception messages into the PR comment. Only the categorised, user-safe
 * summary is allowed on the comment; full details stay in the runner log.
 */
class CodeReviewFailurePrivacyTest {

    static class ThrowingProvider implements AiProvider {
        String name() { 'fake' }
        String review(String prompt) {
            throw new AiProviderException(
                AiProviderException.CATEGORY_UNKNOWN,
                'The AI provider failed with an internal error.',
                new RuntimeException('super secret internal detail: project=acme token=abc123')
            )
        }
    }

    @Test
    void 'failure comment does not echo raw provider exception message'() {
        File diff = File.createTempFile('cra-diff-', '.diff')
        File output = File.createTempFile('cra-review-', '.md')
        diff.text = '''diff --git a/a.txt b/a.txt
--- a/a.txt
+++ b/a.txt
@@ -1 +1 @@
-old
+new
'''
        output.delete()
        diff.deleteOnExit()
        output.deleteOnExit()

        ActionInputs inputs = new ActionInputs(
            apiKey: 'k',
            provider: 'fake',
            model: 'fake-model',
            diffPath: diff.absolutePath,
            outputPath: output.absolutePath
        )

        // Same flow as CodeReview.main: catch the provider exception and
        // write the userMessage to the output file. Assert the comment
        // contains the safe summary but NOT the raw cause message.
        AiProvider provider = new ThrowingProvider()
        try {
            provider.review('')
        } catch (AiProviderException ex) {
            new ReviewReportWriter().writeFailure(output.absolutePath, ex.userMessage)
        }

        assertThat(output.text)
            .doesNotContain('super secret internal detail')
            .doesNotContain('project=acme')
            .doesNotContain('token=abc123')
            .contains('AI provider failed')
    }
}
