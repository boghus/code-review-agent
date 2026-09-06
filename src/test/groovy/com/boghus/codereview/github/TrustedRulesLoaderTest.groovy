package com.boghus.codereview.github

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class TrustedRulesLoaderTest {

    @TempDir
    Path tempDir

    @Test
    void 'loads rules from the base Git tree'() {
        File repo = createRepository()
        String baseSha = git(repo, 'rev-parse', 'HEAD')

        String rules = TrustedRulesLoader.load(
            baseSha,
            '.github/code_review_rules.md',
            tempDir.toFile(),
            repo
        )

        assertThat(rules).isEqualTo('# trusted rules\n')
        assertThat(tempDir.toFile().listFiles()).isEmpty()
    }

    @Test
    void 'deletes temporary rules file when git show fails'() {
        File rulesFile = new File(tempDir.toFile(), 'cra-rules-failure.md')
        rulesFile.createNewFile()

        assertThatThrownBy {
            TrustedRulesLoader.loadFromFile(
                'missing-base-ref',
                '.github/code_review_rules.md',
                new File('.').canonicalFile,
                rulesFile
            )
        }.isInstanceOf(IllegalStateException)

        assertThat(rulesFile).doesNotExist()
    }

    @Test
    void 'deletes temporary rules file when reading the file fails'() {
        File backingFile = new File(tempDir.toFile(), 'cra-rules-read-failure.md')
        backingFile.createNewFile()
        Files.setPosixFilePermissions(backingFile.toPath(), [PosixFilePermission.OWNER_WRITE] as Set)
        File failingReadFile = new FailingReadFile(backingFile)

        assertThatThrownBy {
            TrustedRulesLoader.loadFromFile(
                'HEAD',
                '.github/code_review_rules.md',
                new File('.').canonicalFile,
                failingReadFile
            )
        }.isInstanceOf(RuntimeException)

        assertThat(backingFile).doesNotExist()
    }

    @Test
    void 'rejects absolute and traversal paths'() {
        ['../etc/passwd', 'foo/../../bar', '/etc/passwd', './rules.md', 'foo/./bar.md', 'foo//bar.md', '-rules.md'].each { String path ->
            assertThatThrownBy {
                TrustedRulesLoader.validate('base-sha', path)
            }
                .isInstanceOf(IllegalArgumentException)
        }
    }

    @Test
    void 'treats shell metacharacters as data rather than executable shell syntax'() {
        ['$(touch pwned)', '`touch pwned`', '$(echo malicious)'].each { String path ->
            assertThatThrownBy {
                TrustedRulesLoader.load('base-sha', path, tempDir.toFile())
            }
                .isInstanceOf(IllegalStateException)
        }
    }

    @Test
    void 'rejects missing base SHA'() {
        assertThatThrownBy {
            TrustedRulesLoader.validate('', '.github/code_review_rules.md')
        }
            .isInstanceOf(IllegalArgumentException)
    }

    private File createRepository() {
        File repo = tempDir.resolve('repo').toFile()
        repo.mkdirs()
        new File(repo, '.github').mkdirs()
        new File(repo, '.github/code_review_rules.md').text = '# trusted rules\n'
        git(repo, 'init', '-q')
        git(repo, 'config', 'user.email', 'security-test@example.invalid')
        git(repo, 'config', 'user.name', 'Security Test')
        git(repo, 'add', '.')
        git(repo, 'commit', '-q', '-m', 'test fixture')
        repo
    }

    private static String git(File directory, String... arguments) {
        List<String> command = ['git']
        command.addAll(arguments.toList())
        Process process = new ProcessBuilder(command)
            .directory(directory)
            .start()
        String output = process.inputStream.getText('UTF-8')
        String error = process.errorStream.getText('UTF-8')
        int exitCode = process.waitFor()
        assertThat(exitCode).withFailMessage(error).isZero()
        output.trim()
    }

    private static class FailingReadFile extends File {

        FailingReadFile(File file) {
            super(file.absolutePath)
        }

        @Override
        boolean setReadable(boolean readable, boolean ownerOnly) {
            true
        }
    }
}
