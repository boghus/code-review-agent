package com.boghus.codereview.review

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

class NormalizerSmokeTest {
    @Test
    void 'starts at canonical header'() {
        assertThat(ReviewResponseNormalizer.normalize('x\n## 🤖 Code Review Agent by boghus\ny'))
            .startsWith(ReviewResponseNormalizer.REVIEW_HEADER)
    }
}
