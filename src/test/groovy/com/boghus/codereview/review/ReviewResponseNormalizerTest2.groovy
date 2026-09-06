package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerTest2 {
    @Test
    void 'normalizes provider preamble'() {
        String response = 'Preamble\n\n## 🤖 Code Review Agent by boghus\n\n### Resumen\nTodo correcto.\n'
        assertThat(ReviewResponseNormalizer.normalize(response))
            .isEqualTo('## 🤖 Code Review Agent by boghus\n\n### Resumen\nTodo correcto.')
    }

    @Test
    void 'rejects response without expected header'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('random response') }
            .isInstanceOf(IllegalArgumentException)
    }

    @Test
    void 'keeps valid response unchanged apart from whitespace'() {
        String response = '## 🤖 Code Review Agent by boghus\n\n### Resumen\nOK\n'
        assertThat(ReviewResponseNormalizer.normalize(response)).isEqualTo(response.trim())
    }
}
