package com.boghus.codereview.review

import com.boghus.codereview.provider.ReviewRequest
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewTraceTest {

    @Test
    void 'trace contains metadata and hashes without content'() {
        String rules = 'Never ignore security findings.'
        String diff = '''diff --git a/App.java b/App.java
--- a/App.java
+++ b/App.java
@@ -1 +1 @@
-old
+new
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)
        ReviewRequest request = new ReviewRequest(
            'trusted system secret',
            'trusted developer instructions',
            'review these changes',
            '```diff\n+new\n```'
        )

        ReviewTrace trace = ReviewTrace.create(
            'boghus/code-review-agent',
            64,
            'base123',
            'head456',
            analyzer,
            diff,
            rules,
            request,
            'gemini-test',
            8192
        )

        assertThat(trace.executionId).isNotBlank()
        assertThat(trace.repository).isEqualTo('boghus/code-review-agent')
        assertThat(trace.pullRequest).isEqualTo(64)
        assertThat(trace.baseSha).isEqualTo('base123')
        assertThat(trace.headSha).isEqualTo('head456')
        assertThat(trace.changedFiles).containsExactly('App.java')
        assertThat(trace.diffLines).isGreaterThan(0)
        assertThat(trace.diffBytes).isEqualTo(diff.getBytes('UTF-8').length)
        assertThat(trace.diffSha256).hasSize(64)
        assertThat(trace.rulesSha256).hasSize(64)
        assertThat(trace.model).isEqualTo('gemini-test')
        assertThat(trace.maxOutputTokens).isEqualTo(8192)
        assertThat(trace.promptBytes).isGreaterThan(0)
        assertThat(trace.promptSha256).hasSize(64)
    }

    @Test
    void 'trace is deterministic for content hashes'() {
        String content = 'same content'
        DiffAnalyzer analyzer = DiffAnalyzer.parse('diff --git a/a.txt b/a.txt\n@@ -1 +1 @@\n-old\n+new\n')
        ReviewRequest request = new ReviewRequest('system', 'developer', 'prompt', 'content')

        ReviewTrace first = ReviewTrace.create('repo', 1, 'base', 'head', analyzer, content, 'rules', request, 'model', 8192)
        ReviewTrace second = ReviewTrace.create('repo', 1, 'base', 'head', analyzer, content, 'rules', request, 'model', 8192)

        assertThat(first.executionId).isNotEqualTo(second.executionId)
        assertThat(first.diffSha256).isEqualTo(second.diffSha256)
        assertThat(first.rulesSha256).isEqualTo(second.rulesSha256)
        assertThat(first.promptSha256).isEqualTo(second.promptSha256)
    }
}
