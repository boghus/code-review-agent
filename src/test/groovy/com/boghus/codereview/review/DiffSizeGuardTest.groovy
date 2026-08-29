package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class DiffSizeGuardTest {

    @Test
    void 'accepts diffs under both limits'() {
        DiffSizeGuard guard = new DiffSizeGuard(1_000, 100)
        DiffSizeGuard.DiffSizeDecision decision = guard.evaluate('a\nb\nc')

        assertThat(decision.acceptable).isTrue()
        assertThat(decision.reason).isNull()
        assertThat(decision.lines).isEqualTo(3)
    }

    @Test
    void 'rejects diffs that exceed the byte limit even when lines are few'() {
        DiffSizeGuard guard = new DiffSizeGuard(10, 100)
        DiffSizeGuard.DiffSizeDecision decision = guard.evaluate('x' * 50)

        assertThat(decision.acceptable).isFalse()
        assertThat(decision.reason).contains('bytes')
        assertThat(decision.bytes).isEqualTo(50)
    }

    @Test
    void 'rejects diffs that exceed the line limit'() {
        DiffSizeGuard guard = new DiffSizeGuard(1_000_000, 5)
        StringBuilder diff = new StringBuilder()
        10.times { diff.append("line ${it}\n") }

        DiffSizeGuard.DiffSizeDecision decision = guard.evaluate(diff.toString())

        assertThat(decision.acceptable).isFalse()
        assertThat(decision.reason).contains('lines')
        assertThat(decision.lines).isEqualTo(10)
    }

    @Test
    void 'accepts empty diff'() {
        DiffSizeGuard guard = new DiffSizeGuard()

        assertThat(guard.evaluate('').acceptable).isTrue()
        assertThat(guard.evaluate(null).acceptable).isTrue()
    }
}
