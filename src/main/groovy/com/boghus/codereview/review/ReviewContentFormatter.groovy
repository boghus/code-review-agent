package com.boghus.codereview.review

final class ReviewContentFormatter {

    private ReviewContentFormatter() {
    }

    static String format(ReviewContentType type, String content) {
        return """[${type.label}]
${content ?: ''}
[/${type.label}]"""
    }
}
