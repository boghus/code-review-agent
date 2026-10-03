package com.boghus.codereview.output

import com.boghus.codereview.review.ReviewFinding
import com.boghus.codereview.review.ReviewLanguage
import com.boghus.codereview.review.ReviewResponse
import com.boghus.codereview.review.ReviewSeverity
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
    void 'writeAiGenerated renders a human-readable review from the structured response'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        ReviewResponse response = new ReviewResponse(
            'Se encontró un hallazgo importante.',
            [new ReviewFinding(
                ReviewSeverity.HIGH,
                'Posible NullPointerException',
                'src/Foo.groovy',
                42,
                'El valor puede ser nulo antes de invocar el método.',
                'La ejecución podría fallar en tiempo de ejecución.',
                'Valida el valor antes de utilizarlo.'
            )]
        )

        writer.writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        String content = file.text

        assertThat(content)
            .startsWith(ReviewReportWriter.COMMENT_MARKER + '\n## 🤖 Code Review Agent')
            .contains('**Resumen**')
            .contains('Se encontró un hallazgo importante.')
            .contains('🟠 **HIGH — Posible NullPointerException**')
            .contains('`src/Foo.groovy:42`')
            .contains('**Qué encontramos**')
            .contains('El valor puede ser nulo antes de invocar el método.')
            .contains('**Impacto**')
            .contains('La ejecución podría fallar en tiempo de ejecución.')
            .contains('**Qué hacer**')
            .contains('Valida el valor antes de utilizarlo.')
            .contains('🟠 1 HIGH')
            .contains('0 CRITICAL')
            .contains('0 MEDIUM')
            .contains('0 LOW')
            .contains('⚠️ **AI-generated review:**')
            .doesNotContain('{"summary"')
    }

    @Test
    void 'writeAiGenerated produces a concise no-findings review'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        ReviewResponse response = new ReviewResponse('No findings.', [])

        writer.writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains('**Sin hallazgos.**')
            .contains('No encontramos problemas que requieran cambios en este PR.')
            .contains('**Resumen**')
            .contains('No findings.')
            .contains('0 CRITICAL')
            .contains('0 HIGH')
            .contains('0 MEDIUM')
            .contains('0 LOW')
    }

    @Test
    void 'writeAiGenerated handles optional finding fields'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        ReviewResponse response = new ReviewResponse(
            'Resumen.',
            [new ReviewFinding(
                ReviewSeverity.LOW,
                'Nombre poco descriptivo',
                null,
                null,
                'El nombre dificulta entender la intención del código.',
                null,
                null
            )]
        )

        writer.writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains('🔵 **LOW — Nombre poco descriptivo**')
            .contains('**Qué encontramos**')
            .doesNotContain('**Impacto**')
            .doesNotContain('**Qué hacer**')
    }

    @Test
    void 'write does NOT include AI disclaimer'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        writer.write(file.absolutePath, '## body')

        assertThat(file.text)
            .doesNotContain('⚠️ **AI-generated review:**')
            .doesNotContain('false positives, false negatives')
    }

    @Test
    void 'failure, misconfigured, too-large and empty paths do NOT include AI disclaimer'() {
        File f1 = File.createTempFile('cra-', '.md'); f1.deleteOnExit()
        File f2 = File.createTempFile('cra-', '.md'); f2.deleteOnExit()
        File f3 = File.createTempFile('cra-', '.md'); f3.deleteOnExit()
        File f4 = File.createTempFile('cra-', '.md'); f4.deleteOnExit()

        writer.writeEmpty(f1.absolutePath)
        writer.writeFailure(f2.absolutePath, 'something broke')
        writer.writeMisconfigured(f3.absolutePath, 'missing key')
        writer.writeTooLarge(f4.absolutePath, 'too big', 250_000, 5_500, 200_000, 4_000)

        [f1, f2, f3, f4].each { File f ->
            assertThat(f.text)
                .as('non-AI path %s must not claim AI origin', f.name)
                .doesNotContain('⚠️ **AI-generated review:**')
                .doesNotContain('false positives, false negatives')
        }
    }

    @Test
    void 'normalises trailing whitespace so body always ends with newline before footer'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()

        ReviewResponse response = new ReviewResponse('Resumen.', [])
        new ReviewReportWriter('v1.0.0-rc', 'abc123456789').writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        String body = file.text
        int beforeFooter = body.indexOf('\n---\n🤖 Code Review Agent')
        assertThat(body.substring(0, beforeFooter))
            .endsWith('Please validate the findings before making changes.\n')
    }

    @Test
    void 'labels tag references as "tag" in version footer'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        new ReviewReportWriter('refs/tags/v1.0.0-rc', 'abc123456789').writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains('Version: refs/tags/v1.0.0-rc (tag)')
            .contains('Commit: abc123456789')
    }

    @Test
    void 'labels branch references as "branch" in version footer'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        new ReviewReportWriter('refs/heads/main', 'abc123456789').writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains('Version: refs/heads/main (branch)')
            .contains('Commit: abc123456789')
    }

    @Test
    void 'labels 40-char hex SHA references as "sha" in version footer'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])
        String sha = 'f00c83797e0a2ba46be11d2fcaf6e389247823cc'

        new ReviewReportWriter(sha, sha).writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains("Version: ${sha} (sha)")
            .contains("Commit: ${sha}")
    }

    @Test
    void 'leaves unknown ref formats unlabelled'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        new ReviewReportWriter('refs/pull/42/merge', 'abc123456789').writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .contains('Version: refs/pull/42/merge\n')
            .doesNotContain('Version: refs/pull/42/merge (')
    }

    @Test
    void 'blank ref is treated as absent metadata'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        new ReviewReportWriter('   ', '   ').writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

        assertThat(file.text)
            .doesNotContain('Version:')
            .doesNotContain('Commit:')
    }

    @Test
    void 'does not add version metadata when writer is used outside GitHub Actions'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        writer.writeAiGenerated(file.absolutePath, response, ReviewLanguage.SPANISH)

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
    void 'writeMisconfigured signals skipped review and visible identity'() {
        File file = File.createTempFile('cra-report-', '.md')
        file.deleteOnExit()
        writer.writeMisconfigured(file.absolutePath, 'missing key')

        assertThat(file.text)
            .contains('Code Review Agent by boghus skipped')
            .contains('missing key')
            .contains('non-blocking')
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
        File f1 = File.createTempFile('cra-', '.md'); f1.deleteOnExit()
        File f2 = File.createTempFile('cra-', '.md'); f2.deleteOnExit()
        File f3 = File.createTempFile('cra-', '.md'); f3.deleteOnExit()
        File f4 = File.createTempFile('cra-', '.md'); f4.deleteOnExit()
        File f5 = File.createTempFile('cra-', '.md'); f5.deleteOnExit()
        File f6 = File.createTempFile('cra-', '.md'); f6.deleteOnExit()
        ReviewResponse response = new ReviewResponse('Resumen.', [])

        writer.write(f1.absolutePath, 'x')
        writer.writeAiGenerated(f2.absolutePath, response, ReviewLanguage.SPANISH)
        writer.writeEmpty(f3.absolutePath)
        writer.writeFailure(f4.absolutePath, 'x')
        writer.writeMisconfigured(f5.absolutePath, 'x')
        writer.writeTooLarge(f6.absolutePath, 'x', 1, 1, 1, 1)

        [f1, f2, f3, f4, f5, f6].each { File f ->
            assertThat(f.text)
                .as('body for %s', f.name)
                .doesNotContain(ReviewReportWriter.LEGACY_MARKER)
        }
    }
}
