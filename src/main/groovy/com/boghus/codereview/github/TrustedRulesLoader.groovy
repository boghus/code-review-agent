package com.boghus.codereview.github

import groovy.transform.CompileStatic

/**
 * Loads trusted review rules from the pull request base Git tree.
 *
 * rules-path is untrusted input and is always passed to Git as data through
 * ProcessBuilder. No shell is involved, so shell metacharacters remain data.
 */
@CompileStatic
class TrustedRulesLoader {

    static String load(String baseSha, String rulesPath, File repositoryDirectory) {
        validate(baseSha, rulesPath)

        GitTreeEntry entry = findEntry(baseSha, rulesPath, repositoryDirectory)
        if (entry == null || entry.type != 'blob' || !(entry.mode in ['100644', '100755'])) {
            throw new IllegalArgumentException(
                "rules-path must point to a regular file in base ref '${baseSha}': ${rulesPath}"
            )
        }

        String objectPath = baseSha + ':' + rulesPath
        git(['show', objectPath], repositoryDirectory)
    }

    static void validate(String baseSha, String rulesPath) {
        if (!baseSha?.trim()) {
            throw new IllegalArgumentException('BASE_SHA is required to load trusted rules')
        }
        if (!rulesPath?.trim() || rulesPath.startsWith('/') || rulesPath.startsWith('-')) {
            throw new IllegalArgumentException(
                "rules-path must be a non-empty repository-relative path and must not start with '-': '${rulesPath}'"
            )
        }

        rulesPath.split('/', -1).each { String component ->
            if (!component || component == '.' || component == '..') {
                throw new IllegalArgumentException("rules-path contains an unsafe path component: '${rulesPath}'")
            }
        }
    }

    private static GitTreeEntry findEntry(String baseSha, String rulesPath, File repositoryDirectory) {
        String output = git(['ls-tree', '-z', baseSha, '--', rulesPath], repositoryDirectory)
        String entry = output.split('\u0000', -1).find { String candidate -> candidate }
        if (!entry) {
            return null
        }

        int tab = entry.indexOf('\t')
        if (tab < 0) {
            return null
        }

        List<String> fields = entry.substring(0, tab).split(' ') as List<String>
        String path = entry.substring(tab + 1)
        if (fields.size() != 3 || path != rulesPath) {
            return null
        }
        new GitTreeEntry(fields[0], fields[1])
    }

    private static String git(List<String> arguments, File repositoryDirectory) {
        List<String> command = ['git']
        command.addAll(arguments)
        Process process = new ProcessBuilder(command)
            .directory(repositoryDirectory)
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.getText('UTF-8')
        int exitCode = process.waitFor()
        if (exitCode != 0) {
            throw new IllegalStateException("Git command failed: ${output.trim()}")
        }
        output
    }

    @CompileStatic
    private static class GitTreeEntry {
        final String mode
        final String type

        GitTreeEntry(String mode, String type) {
            this.mode = mode
            this.type = type
        }
    }
}
