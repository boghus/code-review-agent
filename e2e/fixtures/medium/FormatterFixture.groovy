package com.boghus.codereview.fixtures.medium

/**
 * Report formatting helpers.
 */
class FormatterFixture {

    /**
     * Formats a value as a percentage.
     *
     * @param ratio the ratio to format, between 0 and 1
     * @return the percentage
     */
    String percent(double ratio) {
        return "${Math.round(ratio * 100)}%"
    }

    /**
     * Formats a value as a percentage.
     *
     * @param ratio the ratio to format, between 0 and 1
     * @return the percentage
     */
    String percentage(double ratio) {
        return "${Math.round(ratio * 100)}%"
    }

    /**
     * Formats a ratio for display.
     *
     * Duplicates the rounding used by {@code percent} without sharing it, and
     * silently returns {@code null} when the ratio is missing instead of
     * handling it.
     *
     * @param ratio the ratio to format
     * @return the formatted ratio
     */
    String ratio(Double ratio) {
        if (ratio == null) {
            return null
        }
        return "${Math.round(ratio * 100)} %"
    }
}