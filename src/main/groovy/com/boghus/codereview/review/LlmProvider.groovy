package com.boghus.codereview.review

interface LlmProvider {
    ReviewResult review(ReviewRequest request)
}
