package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewResultParserTest {

    private final ReviewResultParser parser = new ReviewResultParser()

    @Test
    void 'parses finding fields from the provider markdown'() {
        String markdown = '''## 🤖 Code Review Agent by boghus

### [HIGH] Incorrect error handling
- **File:** PaymentService.groovy
- **Lines:** 124
 
**Problem:** The exception is swallowed.
**Impact:** Failed payments can be reported as successful.
**Suggested fix:** Preserve the failure and return an error.
**Evidence:** The catch block ignores the exception.
**Verification:** Verified
'''.stripIndent()

        ReviewResult result = parser.parse(markdown)

        assertThat(result.findings).hasSize(1)
        assertThat(result.findings[0].severity).isEqualTo('HIGH')
        assertThat(result.findings[0].title).isEqualTo('Incorrect error handling')
        assertThat(result.findings[0].file).isEqualTo('PaymentService.groovy')
        assertThat(result.findings[0].lines).isEqualTo('124')
        assertThat(result.findings[0].problem).contains('exception is swallowed')
        assertThat(result.findings[0].impact).contains('Failed payments')
        assertThat(result.findings[0].suggestedFix).contains('Preserve the failure')
        assertThat(result.findings[0].verified).isTrue()
    }

    @Test
    void 'parses multiple findings and preserves severity counts'() {
        String markdown = '''### [CRITICAL] Secret exposure
- **File:** Auth.groovy
- **Lines:** 10
**Problem:** Secret is logged.
**Impact:** Credentials may leak.
**Suggested fix:** Remove the secret.
**Evidence:** logger.debug(secret)
**Verification:** Verified

### [LOW] Naming improvement
- **File:** User.groovy
- **Lines:** 20
**Problem:** Name is unclear.
**Impact:** Readability suffers.
**Suggested fix:** Rename the variable.
**Evidence:** The variable name is generic.
**Verification:** Unverified
'''.stripIndent()

        ReviewResult result = parser.parse(markdown)

        assertThat(result.findings).hasSize(2)
        assertThat(result.count('CRITICAL')).isEqualTo(1)
        assertThat(result.count('LOW')).isEqualTo(1)
        assertThat(result.findings[1].verified).isFalse()
    }

    @Test
    void 'ignores markdown without finding headings'() {
        ReviewResult result = parser.parse('No findings were detected.')

        assertThat(result.isEmpty()).isTrue()
    }
}
