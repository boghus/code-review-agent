package com.boghus.codereview.output

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewReportWriterTest {

    private final ReviewReportWriter writer = new ReviewReportWriter()

    @Test
    void 'write prepends idempotency marker and visible identity'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.write(file.absolutePath, '## body')

        String content = file.text
        assertThat(content)
            .startsWith(ReviewReportWriter.COMMENT_MARKER)
            .doesNotContain(ReviewReportWriter.LEGACY_MARKER)
            .endsWith('\n')
    }

    @Test
    void 'writes action ref and commit sha when metadata is available'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        new ReviewReportWriter('v1.0.0-rc', 'abc123456789').write(file.absolutePath, '## body')

        assertThat(file.text)
            .contains('Version: v1.0.0-rc')
            .contains('Commit: abc123456789')
            .contains('\n---\n🤖 Code Review Agent\nVersion: v1.0.0-rc\nCommit: abc123456789\n')
    }

    @Test
    void 'supports a commit sha as action ref'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        new ReviewReportWriter('f00c83797e0a2ba46be11d2fcaf6e389247823cc', 'f00c83797e0a2ba46be11d2fcaf6e389247823cc')
            .write(file.absolutePath, '## body')

        assertThat(file.text)
            .contains('Version: f00c83797e0a2ba46be11d2fcaf6e389247823cc')
            .contains('Commit: f00c83797e0a2ba46be11d2fcaf6e389247823cc')
    }

    @Test
    void 'does not add version metadata when writer is used outside GitHub Actions'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.write(file.absolutePath, '## body')

        assertThat(file.text)
            .doesNotContain('Version:')
            .doesNotContain('Commit:')
    }

    @Test
    void 'new marker is code-review-agent-by-boghus'() {
        assertThat(ReviewReportWriter.COMMENT_MARKER).isEqualTo('<!-- code-review-agent-by-boghus -->')
    }

    @Test
    void 'legacy marker is recognised as a v1 search key only'() {
        // Sanity check: LEGACY_MARKER must NOT be written by any new body,
        // it exists solely so the action's find step can locate v1 comments
        // and replace them with the new marker.
        assertThat(ReviewReportWriter.LEGACY_MARKER).isEqualTo('<!-- code-review-agent -->')
        assertThat(ReviewReportWriter.LEGACY_MARKER).isNotEqualTo(ReviewReportWriter.COMMENT_MARKER)
    }

    @Test
    void 'writeFailure contains visible identity and unavailable header'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.writeFailure(file.absolutePath, 'something broke')

        assertThat(file.text)
            .contains(ReviewReportWriter.COMMENT_MARKER)
            .contains('Code Review Agent by boghus unavailable')
            .contains('something broke')
    }

    @Test
    void 'writeMisconfigured contains setup hint and visible identity'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.writeMisconfigured(file.absolutePath, 'missing key')

        assertThat(file.text)
            .contains('Code Review Agent by boghus is not configured')
            .contains('missing key')
            .contains(ReviewReportWriter.COMMENT_MARKER)
    }

    @Test
    void 'writeEmpty signals no changes and visible identity'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.writeEmpty(file.absolutePath)

        assertThat(file.text)
            .contains(ReviewReportWriter.COMMENT_MARKER)
            .contains('🤖 Code Review Agent by boghus')
            .contains('No changes to review')
    }

    @Test
    void 'writeTooLarge includes sizes, configured limits and visible identity'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.writeTooLarge(file.absolutePath, 'too big', 250_000, 5_500, 200_000, 4_000)

        String body = file.text
        assertThat(body)
            .contains(ReviewReportWriter.COMMENT_MARKER)
            .contains('Code Review Agent by boghus skipped')
            .contains('250000 bytes')
            .contains('5500 lines')
            .contains('200000 bytes')
            .contains('4000 lines')
    }

    @Test
    void 'no body ever carries the legacy marker'() {
        // Defence-in-depth: assert across every writer entry point that the
        // legacy v1 marker is never emitted. The legacy marker exists only
        // so the action's find-comment step can locate v1 comments.
        File f1 = File.createTempFile('cra-', '.md'); f1.deleteOnExit()
        File f2 = File.createTempFile('cra-', '.md'); f2.deleteOnExit()
        File f3 = File.createTempFile('cra-', '.md'); f3.deleteOnExit()
        File f4 = File.createTempFile('cra-', '.md'); f4.deleteOnExit()
        File f5 = File.createTempFile('cra-', '.md'); f5.deleteOnExit()

        writer.write(f1.absolutePath, 'x')
        writer.writeEmpty(f2.absolutePath)
        writer.writeFailure(f3.absolutePath, 'x')
        writer.writeMisconfigured(f4.absolutePath, 'x')
        writer.writeTooLarge(f5.absolutePath, 'x', 1, 1, 1, 1)

        [f1, f2, f3, f4, f5].each { File f ->
            assertThat(f.text)
                .as('body for %s', f.name)
                .doesNotContain(ReviewReportWriter.LEGACY_MARKER)
        }
    }
}
