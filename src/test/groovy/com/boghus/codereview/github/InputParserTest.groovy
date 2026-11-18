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
    void 'parses supported language codes case insensitively and trims whitespace'() {
        assertThat(InputParser.parseLanguage('es')).isEqualTo(ReviewLanguage.SPANISH)
        assertThat(InputParser.parseLanguage(' ES ')).isEqualTo(ReviewLanguage.SPANISH)
        assertThat(InputParser.parseLanguage('en')).isEqualTo(ReviewLanguage.ENGLISH)
        assertThat(InputParser.parseLanguage(' EN ')).isEqualTo(ReviewLanguage.ENGLISH)
    }

    @Test
    void 'uses English without warning when language is omitted'() {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        PrintStream originalOut = System.out
        try {
            System.setOut(new PrintStream(output, true, 'UTF-8'))

            assertThat(InputParser.parseLanguage(null)).isEqualTo(ReviewLanguage.ENGLISH)
            assertThat(InputParser.parseLanguage('')).isEqualTo(ReviewLanguage.ENGLISH)
            assertThat(InputParser.parseLanguage('   ')).isEqualTo(ReviewLanguage.ENGLISH)
        } finally {
            System.setOut(originalOut)
        }

        assertThat(output.toString('UTF-8')).isEmpty()
    }

    @Test
    void 'falls back to English and warns for unsupported language names'() {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        PrintStream originalOut = System.out
        try {
            System.setOut(new PrintStream(output, true, 'UTF-8'))

            assertThat(InputParser.parseLanguage('spanish')).isEqualTo(ReviewLanguage.ENGLISH)
            assertThat(InputParser.parseLanguage('espanol')).isEqualTo(ReviewLanguage.ENGLISH)
        } finally {
            System.setOut(originalOut)
        }

        String warning = output.toString('UTF-8')
        assertThat(warning).contains("⚠️ Idioma no soportado 'spanish'. Se utilizará inglés como alternativa.")
        assertThat(warning).contains("⚠️ Idioma no soportado 'espanol'. Se utilizará inglés como alternativa.")
    }

    @Test
    void 'falls back to English for unknown language codes'() {
        assertThat(InputParser.parseLanguage('fr')).isEqualTo(ReviewLanguage.ENGLISH)
        assertThat(InputParser.parseLanguage('klingon')).isEqualTo(ReviewLanguage.ENGLISH)
    }
}
