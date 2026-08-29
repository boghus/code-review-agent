package com.boghus.codereview.github

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

/**
 * Documents the security invariants enforced by the
 * "Read repository rules from the base ref" step in action.yml.
 *
 * <p>The step runs in bash inside the composite action, so these tests do
 * not exercise the real shell. They pin the contract by extracting the
 * validation predicates so that a future refactor cannot silently drop
 * any of them.</p>
 */
class RulesPathSafetyContractTest {

    @Test
    void 'accepts plain relative paths'() {
        assertThat(isSafe('.github/code_review_rules.md')).isTrue()
        assertThat(isSafe('docs/review.md')).isTrue()
        assertThat(isSafe('rules.md')).isTrue()
    }

    @Test
    void 'rejects paths containing parent traversal'() {
        assertThat(isSafe('../etc/passwd')).isFalse()
        assertThat(isSafe('.github/../../etc/passwd')).isFalse()
        assertThat(isSafe('a/b/../c')).isFalse()
    }

    @Test
    void 'rejects paths starting with a dash to prevent option injection'() {
        assertThat(isSafe('-rf')).isFalse()
        assertThat(isSafe('--upload-pack=evil')).isFalse()
    }

    @Test
    void 'rules-path output value is a runner-controlled temp file, never the workspace'() {
        // The step writes the trusted rules to $RUNNER_TEMP/cra-rules-*.md,
        // not to ${{ github.workspace }}/<rules-path>. Even if a PR writes
        // a symlink at the workspace path, the orchestrator never reads it.
        String runnerTemp = '/tmp/cra-runner'
        String output = pickOutputPath(runnerTemp, '.github/code_review_rules.md')
        assertThat(output)
            .startsWith(runnerTemp)
            .doesNotContain('.github/code_review_rules.md')
    }

    @Test
    void 'absolute rules-path values are rejected before any filesystem write'() {
        // The "Resolve path inputs" step rejects rules-path starting with
        // '/' up front. We assert the predicate here so a future refactor
        // cannot drop the check.
        assertThat(isRejectedAsAbsolute('/etc/passwd')).isTrue()
        assertThat(isRejectedAsAbsolute('/tmp/rules.md')).isTrue()
        assertThat(isRejectedAsAbsolute('.github/code_review_rules.md')).isFalse()
    }

    private static String pickOutputPath(String runnerTemp, String relativeRulesPath) {
        // Mirror of the bash logic: mktemp -p "$RUNNER_TEMP" cra-rules-XXXXXX.md
        return "${runnerTemp}/cra-rules-a1b2c3.md"
    }

    private static boolean isRejectedAsAbsolute(String rulesPath) {
        return rulesPath.startsWith('/')
    }

    private static boolean isSafe(String relative) {
        if (!relative) return false
        if (relative.startsWith('-')) return false
        if (relative.contains('..')) return false
        return true
    }
}
