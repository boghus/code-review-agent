package com.boghus.codereview.review

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * Verifies that {@link DiffBuilder} returns the diff between BASE and HEAD
 * only, never content from intermediate commits.
 *
 * <p>Regression for issue #68: a file present in an intermediate commit but
 * removed in HEAD must not appear in the generated diff.</p>
 */
class DiffBuilderTest {

    private File repoDir
    private File diffFile

    @BeforeEach
    void setUp() {
        repoDir = File.createTempDir('cra-diffbuilder-', '.dir')
        runGit('init', '-q')
        runGit('config', 'user.name', 'Code Review Agent')
        runGit('config', 'user.email', 'cra@example.invalid')
    }

    @AfterEach
    void tearDown() {
        if (repoDir != null) {
            repoDir.deleteDir()
        }
        if (diffFile != null) {
            diffFile.delete()
        }
    }

    @Test
    void 'rejects blank base sha'() {
        diffFile = File.createTempFile('cra-diff-', '.diff')
        assertThatThrownBy({ DiffBuilder.build(diffFile, '', 'head') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('baseSha')
    }

    @Test
    void 'rejects blank head sha'() {
        diffFile = File.createTempFile('cra-diff-', '.diff')
        assertThatThrownBy({ DiffBuilder.build(diffFile, 'base', '') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('headSha')
    }

    @Test
    void 'rejects sha values that start with a dash'() {
        diffFile = File.createTempFile('cra-diff-', '.diff')

        assertThatThrownBy({ DiffBuilder.build(diffFile, '--exec=bad', 'head') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('must not start with')

        assertThatThrownBy({ DiffBuilder.build(diffFile, 'base', '--output=bad') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('must not start with')
    }

    @Test
    void 'rejects non-positive context lines'() {
        diffFile = File.createTempFile('cra-diff-', '.diff')
        assertThatThrownBy({ DiffBuilder.build(diffFile, 'base', 'head', 0) })
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('contextLines')
    }

    @Test
    void 'writes empty diff when base equals head'() {
        File kept = new File(repoDir, 'kept.txt')
        kept.text = 'stable'
        runGit('add', 'kept.txt')
        runGit('commit', '-qm', 'base')
        String sha = runGitAndCapture('rev-parse', 'HEAD')

        diffFile = File.createTempFile('cra-diff-', '.diff')
        int lines = DiffBuilder.build(diffFile, sha, sha, DiffBuilder.DEFAULT_CONTEXT_LINES, repoDir)

        assertThat(lines).isEqualTo(0)
        assertThat(diffFile.text).isEmpty()
    }

    @Test
    void 'deletes output when git diff fails'() {
        diffFile = File.createTempFile('cra-diff-', '.diff')
        diffFile.text = 'stale diff content'

        assertThatThrownBy({
            DiffBuilder.build(diffFile, 'missing-base-sha', 'missing-head-sha',
                DiffBuilder.DEFAULT_CONTEXT_LINES, repoDir)
        })
            .isInstanceOf(IllegalStateException)
            .hasMessageContaining('git diff failed')

        assertThat(diffFile).doesNotExist()
    }

    @Test
    void 'omits content of intermediate commits removed in head (#68)'() {
        // base -> intermediate (adds historical.txt) -> final (removes historical.txt, edits kept.txt)
        // Regression for PR #67: historical.txt must not appear in the diff.
        File kept = new File(repoDir, 'kept.txt')
        kept.text = 'stable'
        runGit('add', 'kept.txt')
        runGit('commit', '-qm', 'base')
        String baseSha = runGitAndCapture('rev-parse', 'HEAD')

        File historical = new File(repoDir, 'historical.txt')
        historical.text = 'historical change'
        runGit('add', 'historical.txt')
        runGit('commit', '-qm', 'intermediate change')

        kept.text = 'stable\nfinal change'
        historical.delete()
        runGit('add', '-A')
        runGit('commit', '-qm', 'final PR state')
        String headSha = runGitAndCapture('rev-parse', 'HEAD')

        diffFile = File.createTempFile('cra-diff-', '.diff')
        DiffBuilder.build(diffFile, baseSha, headSha, DiffBuilder.DEFAULT_CONTEXT_LINES, repoDir)

        String diff = diffFile.text
        assertThat(diff)
            .as('intermediate commit content must not leak into the final PR diff')
            .doesNotContain('historical change')
            .doesNotContain('historical.txt')
        assertThat(diff)
            .contains('kept.txt')
            .contains('+final change')
    }

    private void runGit(String... args) {
        ExecResult result = runGitCapturing(args)
        if (result.exit != 0) {
            throw new IllegalStateException(
                "git ${args.join(' ')} failed: exit=${result.exit}, output=${result.output}"
            )
        }
    }

    private String runGitAndCapture(String... args) {
        ExecResult result = runGitCapturing(args)
        if (result.exit != 0) {
            throw new IllegalStateException(
                "git ${args.join(' ')} failed: exit=${result.exit}, output=${result.output}"
            )
        }
        return result.output.trim()
    }

    private ExecResult runGitCapturing(String... args) {
        List<String> command = new ArrayList<>()
        command.add('git')
        command.addAll(args as List<String>)
        ProcessBuilder pb = new ProcessBuilder(command)
        pb.directory(repoDir)
        pb.redirectErrorStream(true)
        Process process = pb.start()
        String output = process.inputStream.text
        int exit = process.waitFor()
        return new ExecResult(exit: exit, output: output)
    }

    private static class ExecResult {
        int exit
        String output
    }
}
