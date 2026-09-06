package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerTest18 {
    @Test
    void 'rejects response without header'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('invalid') }
            .isInstanceOf(IllegalArgumentException)
    }
}
