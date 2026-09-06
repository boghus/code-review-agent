package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseNormalizerMissingTest {
    @Test
    void 'throws when canonical header is absent'() {
        assertThatThrownBy { ReviewResponseNormalizer.normalize('invalid') }
            .isInstanceOf(IllegalArgumentException)
    }
}
