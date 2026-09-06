package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerTest {

    @Test
    void 'keeps a response that already starts with the review header'() {
        String response = '''## 🤖 Code Review Agent by boghus

### Resumen
Todo correcto.
'''

        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo(response.trim())
    }

    @Test
    void 'removes content before the review header'() {
        String response = '''Some analysis...

Let me review the changes.

## 🤖 Code Review Agent by boghus

### Resumen
Todo correcto.
'''

        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('''## 🤖 Code Review Agent by boghus

### Resumen
Todo correcto.''')
    }

    @Test
    void 'fails explicitly when the review header is missing'() {
        assertThatThrownBy {
            ReviewResponseNormalizer.normalize('Some random AI response')
        }
            .isInstanceOf(IllegalArgumentException)
            .hasMessage('The AI provider response does not contain the expected review header.')
    }

    @Test
    void 'keeps an empty response empty'() {
        assertThat(ReviewResponseNormalizer.normalize('')).isEmpty()
    }
}
