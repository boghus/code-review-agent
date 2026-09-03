package com.boghus.codereview.github

import com.boghus.codereview.review.ReviewLanguage
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

    @Test
    void 'parses Spanish language'() {
        assertThat(InputParser.parseLanguage('es')).isEqualTo(ReviewLanguage.SPANISH)
    }

    @Test
    void 'parses English language'() {
        assertThat(InputParser.parseLanguage('en')).isEqualTo(ReviewLanguage.ENGLISH)
    }

    @Test
    void 'parses language case insensitively and trims whitespace'() {
        assertThat(InputParser.parseLanguage(' ES ')).isEqualTo(ReviewLanguage.SPANISH)
    }

    @Test
    void 'falls back to English for blank or invalid language'() {
        assertThat(InputParser.parseLanguage(null)).isEqualTo(ReviewLanguage.ENGLISH)
        assertThat(InputParser.parseLanguage('')).isEqualTo(ReviewLanguage.ENGLISH)
        assertThat(InputParser.parseLanguage('fr')).isEqualTo(ReviewLanguage.ENGLISH)
    }
}
