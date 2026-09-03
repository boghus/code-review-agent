package com.boghus.codereview.github

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class InputParserTest {

    @Test
    void 'parses positive integers'() {
        assertThat(InputParser.parsePositiveInt('1', 100)).isEqualTo(1)
        assertThat(InputParser.parsePositiveInt('42', 100)).isEqualTo(42)
    }

    @Test
    void 'trims whitespace before parsing'() {
        assertThat(InputParser.parsePositiveInt(' 42 ', 100)).isEqualTo(42)
    }

    @Test
    void 'uses fallback for blank values'() {
        assertThat(InputParser.parsePositiveInt(null, 100)).isEqualTo(100)
        assertThat(InputParser.parsePositiveInt('', 100)).isEqualTo(100)
        assertThat(InputParser.parsePositiveInt('   ', 100)).isEqualTo(100)
    }

    @Test
    void 'uses fallback for zero and negative values'() {
        assertThat(InputParser.parsePositiveInt('0', 100)).isEqualTo(100)
        assertThat(InputParser.parsePositiveInt('-1', 100)).isEqualTo(100)
    }

    @Test
    void 'uses fallback for malformed values'() {
        assertThat(InputParser.parsePositiveInt('not-a-number', 100)).isEqualTo(100)
    }

    @Test
    void 'does not truncate or overflow the parsed value'() {
        assertThat(InputParser.parsePositiveInt('2147483647', 100)).isEqualTo(Integer.MAX_VALUE)
        assertThat(InputParser.parsePositiveInt('2147483648', 100)).isEqualTo(100)
    }
}
