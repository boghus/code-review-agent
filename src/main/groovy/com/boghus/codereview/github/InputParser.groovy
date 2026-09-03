package com.boghus.codereview.github

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
}
