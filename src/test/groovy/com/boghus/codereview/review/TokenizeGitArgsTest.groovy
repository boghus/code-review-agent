package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for the path tokenizer used inside DiffAnalyzer. These pin
 * the exact contract: a single state machine that handles quoting and
 * C-style escapes together, so \" inside a quoted region is never
 * mistaken for a closing quote.
 */
class TokenizeGitArgsTest {

    @Test
    void 'tokenizes two simple unquoted paths'() {
        List<String> tokens = DiffAnalyzer.tokenizeGitArgsForTest('a/Foo.java b/Bar.java')
        assertThat(tokens).containsExactly('a/Foo.java', 'b/Bar.java')
    }

    @Test
    void 'tokenizes two quoted paths containing spaces'() {
        List<String> tokens = DiffAnalyzer.tokenizeGitArgsForTest(
            '"a/path with spaces/File.java" "b/path with spaces/File.java"'
        )
        assertThat(tokens).containsExactly(
            'a/path with spaces/File.java',
            'b/path with spaces/File.java'
        )
    }

    @Test
    void 'decodes escaped quote inside quoted path'() {
        // In runtime the literal contains backslash + quote inside the
        // surrounding quotes. Git escapes an embedded quote this way.
        List<String> tokens = DiffAnalyzer.tokenizeGitArgsForTest(
            '"a/he said \\"hi\\"/File.java" "b/he said \\"hi\\"/File.java"'
        )
        assertThat(tokens).containsExactly(
            'a/he said "hi"/File.java',
            'b/he said "hi"/File.java'
        )
    }

    @Test
    void 'decodes double backslash as single backslash'() {
        // In runtime the literal contains two backslashes inside the
        // quoted region, which git uses to represent one literal backslash.
        List<String> tokens = DiffAnalyzer.tokenizeGitArgsForTest(
            '"a/dir\\\\sub/File.java" "b/dir\\\\sub/File.java"'
        )
        assertThat(tokens).containsExactly(
            'a/dir\\sub/File.java',
            'b/dir\\sub/File.java'
        )
    }
}
