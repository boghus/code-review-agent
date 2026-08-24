package com.boghus.codereview.review

interface ReviewEngine {
    ReviewResult review(ReviewRequest request)
}
