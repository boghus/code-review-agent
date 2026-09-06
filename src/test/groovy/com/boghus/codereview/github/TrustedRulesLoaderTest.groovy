package com.boghus.codereview.github

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class TrustedRulesLoaderTest {

    @TempDir
    Path tempDir

    @Test
    void 'loads rules from the requested base tree blob'() {
        File repository = createRepository('rules.md', '# trusted rules')
        String baseSha = git(repository, 'rev-parse', 'HEAD')

        String rules = TrustedRulesLoader.load(baseSha, 'rules.md', repository)

        assertThat(rules).isEqualTo('# trusted rules')
    }

    @Test
    void 'does not interpret shell metacharacters in rules path'() {
        File repository = createRepository('rules.md', '# trusted rules')
        String baseSha = git(repository, 'rev-parse', 'HEAD')
        File marker = new File(repository, 'created-by-shell')
        String payload = "rules.md;touch ${marker.absolutePath}"

        assertThatThrownBy { TrustedRulesLoader.load(baseSha, payload, repository) }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('regular file')
        assertThat(marker).doesNotExist()
    }

    @Test
    void 'rejects empty and unsafe path components'() {
        ['../rules.md', 'rules/../rules.md', 'rules//file.md', './rules.md', 'rules/./file.md', ''].each { String path ->
            assertThatThrownBy { TrustedRulesLoader.validate('abc123', path) }
                .isInstanceOf(IllegalArgumentException)
        }
    }

    @Test
    void 'rejects absolute paths and option-like paths'() {
        ['/rules.md', '-rules.md', '--rules.md'].each { String path ->
            assertThatThrownBy { TrustedRulesLoader.validate('abc123', path) }
                .isInstanceOf(IllegalArgumentException)
        }
    }

    @Test
    void 'requires base SHA'() {
        assertThatThrownBy { TrustedRulesLoader.validate('', 'rules.md') }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('BASE_SHA')
    }

    @Test
    void 'rejects directories instead of treating them as rules'() {
        File repository = createRepository('rules/rules.md', '# trusted rules')
        String baseSha = git(repository, 'rev-parse', 'HEAD')

        assertThatThrownBy { TrustedRulesLoader.load(baseSha, 'rules', repository) }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('regular file')
    }

    @Test
    void 'rejects symlinks from the base tree'() {
        File repository = new File(tempDir.toFile(), 'symlink-repo')
        repository.mkdirs()
        git(repository, 'init')
        git(repository, 'config', 'user.email', 'test@example.com')
        git(repository, 'config', 'user.name', 'Test')
        new File(repository, 'real.md').text = '# trusted rules'
        java.nio.file.Files.createSymbolicLink(repository.toPath().resolve('rules.md'), Path.of('real.md'))
        git(repository, 'add', '.')
        git(repository, 'commit', '-m', 'fixture')
        String baseSha = git(repository, 'rev-parse', 'HEAD')

        assertThatThrownBy { TrustedRulesLoader.load(baseSha, 'rules.md', repository) }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('regular file')
    }

    @Test
    void 'fails when rules do not exist in base tree'() {
        File repository = createRepository('rules.md', '# trusted rules')
        String baseSha = git(repository, 'rev-parse', 'HEAD')

        assertThatThrownBy { TrustedRulesLoader.load(baseSha, 'missing.md', repository) }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('regular file')
    }

    private File createRepository(String path, String content) {
        File repository = new File(tempDir.toFile(), "repo-${System.nanoTime()}")
        repository.mkdirs()
        git(repository, 'init')
        git(repository, 'config', 'user.email', 'test@example.com')
        git(repository, 'config', 'user.name', 'Test')
        File rules = new File(repository, path)
        rules.parentFile.mkdirs()
        rules.text = content
        git(repository, 'add', '.')
        git(repository, 'commit', '-m', 'fixture')
        repository
    }

    private static String git(File directory, String... arguments) {
        List<String> command = ['git']
        command.addAll(arguments as List<String>)
        Process process = new ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.getText('UTF-8').trim()
        int exitCode = process.waitFor()
        assertThat(exitCode).withFailMessage("git failed: ${output}").isZero()
        output
    }
}
