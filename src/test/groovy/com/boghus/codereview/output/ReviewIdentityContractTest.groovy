package com.boghus.codereview.output

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * Pins the v2 identity contract: the visible name and the new marker are
 * stable, and the legacy v1 marker is recognised only as a search key.
 */
class ReviewIdentityContractTest {

    private final ReviewReportWriter writer = new ReviewReportWriter()

    @Test
    void 'every body carries the new marker exactly once at the top'() {
        File file = File.createTempFile('cra-', '.md')
        file.deleteOnExit()

        writer.write(file.absolutePath, '## 🤖 Code Review Agent by boghus\nbody')

        String body = file.text
        assertThat(body)
            .startsWith(ReviewReportWriter.COMMENT_MARKER)
            .contains('🤖 Code Review Agent by boghus')
        // Exactly one occurrence (the one at the top) — guards against
        // accidental double-prefixing if a future refactor calls write()
        // twice.
        assertThat((body.split(ReviewReportWriter.COMMENT_MARKER, -1) as List).size() - 1)
            .isEqualTo(1)
    }

    @Test
    void 'new marker does not match gemini review marker'() {
        // Isolation requirement: comments from other reviewers (e.g. the
        // legacy Gemini Code Review with marker '<!-- gemini-review -->')
        // must not be picked up by find-comment. Since the action searches
        // for the exact '<!-- code-review-agent-by-boghus -->' string (or
        // the legacy v1 one), an unrelated marker never satisfies the
        // regex.
        String geminiBody = '<!-- gemini-review -->\n## 🤖 Gemini Review\nfoo'
        assertThat(geminiBody.contains(ReviewReportWriter.COMMENT_MARKER)).isFalse()
        assertThat(geminiBody.contains(ReviewReportWriter.LEGACY_MARKER)).isFalse()
    }

    @Test
    void 'legacy v1 comment would be recognised by the search regex'() {
        // This documents the migration behaviour: a comment written by v1
        // ('<!-- code-review-agent -->') is located by the new find-comment
        // step and replaced with the new marker on the next run.
        String legacyBody = '<!-- code-review-agent -->\n## Code Review Agent\nfoo'
        assertThat(legacyBody.contains(ReviewReportWriter.LEGACY_MARKER)).isTrue()
    }

    @Test
    void 'every output branch carries the new identity header'() {
        File emptyFile = File.createTempFile('cra-', '.md'); emptyFile.deleteOnExit()
        File failureFile = File.createTempFile('cra-', '.md'); failureFile.deleteOnExit()
        File misconfigFile = File.createTempFile('cra-', '.md'); misconfigFile.deleteOnExit()
        File tooLargeFile = File.createTempFile('cra-', '.md'); tooLargeFile.deleteOnExit()

        writer.writeEmpty(emptyFile.absolutePath)
        writer.writeFailure(failureFile.absolutePath, 'broken')
        writer.writeMisconfigured(misconfigFile.absolutePath, 'no key')
        writer.writeTooLarge(tooLargeFile.absolutePath, 'too big', 1, 1, 1, 1)

        // The visible identity is "Code Review Agent by boghus". The success
        // branch uses 🤖 and the warning branches use ⚠️ as a prefix, but
        // every body carries the same identity string.
        [emptyFile, failureFile, misconfigFile, tooLargeFile].each { File f ->
            assertThat(f.text)
                .as('body for %s', f.name)
                .contains('Code Review Agent by boghus')
        }
    }
}
