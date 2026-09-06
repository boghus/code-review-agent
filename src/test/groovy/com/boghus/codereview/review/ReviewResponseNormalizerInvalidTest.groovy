package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerInvalidTest {
    @Test
    void 'rejects response without canonical header'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('random response') }
            .isInstanceOf(IllegalArgumentException)
    }
}
