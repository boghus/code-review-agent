package com.boghus.codereview.github

import com.boghus.codereview.review.ReviewLanguage
import groovy.transform.CompileStatic

/**
 * Parses Action input values and applies validation rules that are part of
 * the input contract.
 *
 * Parsing helpers belong here rather than in ActionInputs so that the class
 * responsible for reading the GitHub Actions environment remains an adapter.
 */
@CompileStatic
class InputParser {

    /**
     * Parses a positive integer, returning the supplied fallback when the
     * input is blank, malformed, zero, or negative.
     *
     * The fallback behavior is intentional: these values are configuration
     * limits, so an invalid optional input must not make the whole action fail.
     */
    static int parsePositiveInt(String raw, int fallback) {
        if (!raw?.trim()) return fallback

        try {
            int value = Integer.parseInt(raw.trim())
            return value > 0 ? value : fallback
        } catch (NumberFormatException ignored) {
            return fallback
        }
    }

    /**
     * Resolves the externally supplied language to a supported enum value.
     * Only stable language codes are accepted; arbitrary labels never reach
     * the prompt. Missing values silently use English, while unsupported
     * non-blank values produce a warning and also fall back to English.
     */
    static ReviewLanguage parseLanguage(String raw) {
        String value = raw?.trim()?.toLowerCase(Locale.ROOT)
        if (!value) return ReviewLanguage.ENGLISH

        ReviewLanguage language = ReviewLanguage.fromCode(value)
        if (language != null) return language

        String displayedValue = value.replaceAll('[\\r\\n\\t]', ' ')
        if (displayedValue.length() > 80) {
            displayedValue = displayedValue.substring(0, 80) + '...'
        }
        println "⚠️ Idioma no soportado '${displayedValue}'. Se utilizará inglés como alternativa."
        return ReviewLanguage.ENGLISH
    }
}
