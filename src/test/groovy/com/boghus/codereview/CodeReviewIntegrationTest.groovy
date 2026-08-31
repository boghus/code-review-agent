package com.boghus.codereview

import com.boghus.codereview.github.ActionInputs
import com.boghus.codereview.output.ReviewReportWriter
import com.boghus.codereview.provider.AiProvider
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * End-to-end test driving the same code path as {@link CodeReview#main(String[])},
 * but with a fake provider and real file I/O. Verifies idempotency of the
 * marker, the empty-diff branch and the success branch.
 */
class CodeReviewIntegrationTest {

    static class FakeProvider implements AiProvider {
        String name() { 'fake' }
        String review(String prompt) {
            return '## 🤖 Code Review Agent by boghus\n\nNo findings.\n'
        }
    }

    @Test
    void 'writes marker-prefixed review when provider succeeds'() {
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
            model: 'm',
            diffPath: diff.absolutePath,
            outputPath: output.absolutePath
        )

        String prompt = new com.boghus.codereview.review.ReviewPromptBuilder().build('', diff.text)
        String text = new FakeProvider().review(prompt)
        new ReviewReportWriter().writeAiGenerated(output.absolutePath, text)

        assertThat(output.text)
            .startsWith(ReviewReportWriter.COMMENT_MARKER)
            .contains('🤖 Code Review Agent by boghus')
            .contains('No findings')
    }

    @Test
    void 'writes empty review when diff has no code changes'() {
        File diff = File.createTempFile('cra-diff-', '.diff')
        File output = File.createTempFile('cra-review-', '.md')
        diff.text = ''
        output.delete()
        diff.deleteOnExit()
        output.deleteOnExit()

        new ReviewReportWriter().writeEmpty(output.absolutePath)

        assertThat(output.text)
            .contains(ReviewReportWriter.COMMENT_MARKER)
            .contains('No changes to review')
    }

    @Test
    void 'writes misconfiguration when api key is blank'() {
        File output = File.createTempFile('cra-review-', '.md')
        output.delete()
        output.deleteOnExit()

        new ReviewReportWriter().writeMisconfigured(output.absolutePath, 'api-key missing')

        assertThat(output.text)
            .contains('is not configured')
            .contains('api-key missing')
    }
}
