package com.boghus.codereview.output

import com.boghus.codereview.review.Finding
import com.boghus.codereview.review.ReviewResult
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class MarkdownReviewFormatterTest {

    private final MarkdownReviewFormatter formatter = new MarkdownReviewFormatter()

    @Test
    void 'renders a minimal result when there are no findings'() {
        String markdown = formatter.format(new ReviewResult())

        assertThat(markdown)
            .contains('### ✅ Sin hallazgos')
            .contains('🔴 0 Critical · 🟠 0 High · 🟡 0 Medium · 🔵 0 Low')
            .contains('Tu PR está listo para revisión.')
            .doesNotContain('### 🚨 Hallazgos')
    }

    @Test
    void 'renders an explicit parsing failure instead of a false clean result'() {
        String markdown = formatter.format(new ReviewResult([], false))

        assertThat(markdown)
            .contains('### ⚠️ No se pudo interpretar el resultado')
            .contains('No podemos afirmar que no existan hallazgos.')
            .doesNotContain('Sin hallazgos')
    }

    @Test
    void 'renders recommendations for medium and low findings'() {
        Finding finding = finding('MEDIUM', 'Duplicated validation')
        String markdown = formatter.format(new ReviewResult([finding]))

        assertThat(markdown)
            .contains('### ⚠️ Hay puntos a revisar')
            .contains('🟡 1 Medium')
            .contains('### 💡 Recomendaciones')
            .contains('UserService.groovy:42')
            .contains('🔴 CRITICAL: 0')
            .contains('🟡 MEDIUM: 1')
            .doesNotContain('Changes requested')
    }

    @Test
    void 'renders grouped action items when critical or high findings exist'() {
        Finding critical = finding('CRITICAL', 'Sensitive data in logs')
        Finding high = finding('HIGH', 'Incorrect error handling')
        String markdown = formatter.format(new ReviewResult([critical, high]))

        assertThat(markdown)
            .contains('### ❌ Changes requested')
            .contains('🔴 1 Critical · 🟠 1 High')
            .contains('- [ ] Resolver los 1 hallazgo Critical')
            .contains('- [ ] Resolver 1 hallazgo High')
            .contains('- [ ] Fix it')
            .contains('### ⚠️ Nivel de riesgo')
    }

    @Test
    void 'omits zero severity action items'() {
        Finding critical = finding('CRITICAL', 'Only critical finding')
        String markdown = formatter.format(new ReviewResult([critical]))

        assertThat(markdown)
            .contains('- [ ] Resolver los 1 hallazgo Critical')
            .doesNotContain('Resolver 0 hallazgo')
            .doesNotContain('Resolver los 0 hallazgos High')
    }

    @Test
    void 'counts every severity exactly'() {
        List<Finding> findings = [
            finding('CRITICAL', 'Critical'),
            finding('HIGH', 'High'),
            finding('MEDIUM', 'Medium 1'),
            finding('MEDIUM', 'Medium 2'),
            finding('LOW', 'Low')
        ]

        String markdown = formatter.format(new ReviewResult(findings))

        assertThat(markdown)
            .contains('🔴 CRITICAL: 1')
            .contains('🟠 HIGH: 1')
            .contains('🟡 MEDIUM: 2')
            .contains('🔵 LOW: 1')
    }

    private static Finding finding(String severity, String title) {
        new Finding(severity, title, 'UserService.groovy', '42', 'Problem', 'Impact', 'Fix it', 'Code evidence', true)
    }
}
