package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerNoHeaderTest {
    @Test
    void 'rejects response without expected review header'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('invalid') }
            .isInstanceOf(IllegalArgumentException)
    }
}
