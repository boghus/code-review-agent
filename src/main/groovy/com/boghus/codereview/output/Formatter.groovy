package com.boghus.codereview.output

import com.boghus.codereview.review.ReviewResult

interface Formatter {
    String format(ReviewResult result)
}
