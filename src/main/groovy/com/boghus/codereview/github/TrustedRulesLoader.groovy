package com.boghus.codereview.github

import groovy.transform.CompileStatic

/**
 * Loads review rules from the pull request base Git tree.
 *
 * rules-path is untrusted input and is deliberately treated as data. Git is
 * invoked directly through ProcessBuilder; no shell is involved. Only a
 * regular blob from BASE_SHA can become trusted review instructions.
 */
@CompileStatic
class TrustedRulesLoader {

    static String load(String baseSha, String rulesPath, File tempDirectory) {
        load(baseSha, rulesPath, tempDirectory, new File('.').canonicalFile)
    }

    static String load(String baseSha, String rulesPath, File tempDirectory, File repositoryDirectory) {
        validate(baseSha, rulesPath)

        String treeOutput = git(['ls-tree', '-z', baseSha, '--', rulesPath], repositoryDirectory)
        String entryType = findEntryType(treeOutput, rulesPath)

        if (entryType != 'blob') {
            throw new IllegalArgumentException("rules-path must point to a regular file in base ref '${baseSha}': ${rulesPath}")
        }

        File rulesFile = File.createTempFile('cra-rules-', '.md', tempDirectory)
        loadFromFile(baseSha, rulesPath, repositoryDirectory, rulesFile)
    }

    static String loadFromFile(String baseSha, String rulesPath, File repositoryDirectory, File rulesFile) {
        try {
            Process process = new ProcessBuilder('git', 'show', "${baseSha}:${rulesPath}")
                .directory(repositoryDirectory)
                .redirectErrorStream(false)
                .start()
            process.inputStream.withStream { InputStream input ->
                rulesFile.withOutputStream { OutputStream output ->
                    input.transferTo(output)
                }
            }
            String error = process.errorStream.getText('UTF-8')
            int exitCode = process.waitFor()
            if (exitCode != 0) {
                throw new IllegalStateException("Unable to read trusted rules from base ref '${baseSha}': ${error.trim()}")
            }

            rulesFile.setReadable(true, true)
            return rulesFile.getText('UTF-8')
        } finally {
            rulesFile.delete()
        }
    }

    private static String findEntryType(String treeOutput, String rulesPath) {
        treeOutput.split('\u0000', -1)
            .findResult { String entry ->
                int tab = entry.indexOf('\t')
                if (tab < 0) {
                    return null
                }
                String metadata = entry.substring(0, tab)
                String path = entry.substring(tab + 1)
                List<String> fields = metadata.split(' ') as List<String>
                fields.size() == 3 && path == rulesPath ? fields[1] : null
            }
    }

    static void validate(String baseSha, String rulesPath) {
        if (!baseSha?.trim()) {
            throw new IllegalArgumentException('BASE_SHA is required to load trusted rules')
        }
        if (!rulesPath?.trim() || rulesPath.startsWith('/') || rulesPath.startsWith('-')) {
            throw new IllegalArgumentException("rules-path must be a non-empty repository-relative path and must not start with '-': '${rulesPath}'")
        }

        rulesPath.split('/', -1).each { String component ->
            if (!component || component == '.' || component == '..') {
                throw new IllegalArgumentException("rules-path contains an unsafe path component: '${rulesPath}'")
            }
        }
    }

    private static String git(List<String> arguments, File repositoryDirectory) {
        List<String> command = ['git']
        command.addAll(arguments)
        Process process = new ProcessBuilder(command)
            .directory(repositoryDirectory)
            .start()
        String output = process.inputStream.getText('UTF-8')
        String error = process.errorStream.getText('UTF-8')
        int exitCode = process.waitFor()
        if (exitCode != 0) {
            throw new IllegalStateException("Git command failed: ${error.trim()}")
        }
        output
    }
}
