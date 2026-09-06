package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerTest3 {
    @Test
    void 'rejects response without review header'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('random response') }
            .isInstanceOf(IllegalArgumentException)
    }
}
