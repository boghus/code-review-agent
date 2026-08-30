package com.boghus.codereview.review

import groovy.transform.CompileStatic

import java.util.ArrayList
import java.util.regex.Matcher

/**
 * Parses a unified diff produced by {@code git diff} and exposes the lines
 * actually added by the PR. Used to validate that any line numbers the AI
 * reports are anchored in real changes.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #lineBelongsToChange(String, int)} only accepts {@code +}
 *       (added) lines, never {@code -} (removed) lines. Findings must point
 *       to lines the reviewer can see in the merged result; deleted lines
 *       are out of scope.</li>
 *   <li>Renames, copies and deletions are tracked so the touched-files
 *       set reflects the actual file list of the PR.</li>
 *   <li>File paths are extracted respecting git's C-style quoting, so paths
 *       containing spaces or other special characters round-trip correctly.</li>
 * </ul>
 */
@CompileStatic
class DiffAnalyzer {

    // (no regex constants needed; parsing is done in a single state machine.)

    private final List<ChangedLine> addedLines = []
    private final Set<String> touchedFiles = new LinkedHashSet<>()

    static DiffAnalyzer parse(String diff) {
        DiffAnalyzer analyzer = new DiffAnalyzer()
        if (!diff) {
            return analyzer
        }

        String currentFile = null
        boolean inRenameBlock = false
        boolean inHunk = false
        int lineNumber = 0

        diff.split('\n').each { String line ->
            if (line.startsWith('diff --git')) {
                String[] paths = extractGitDiffPaths(line)
                String target = paths != null ? paths[1] : null
                currentFile = target
                inRenameBlock = false
                inHunk = false
                lineNumber = 0
                if (currentFile) {
                    analyzer.touchedFiles << currentFile
                }
            } else if (line.startsWith('rename from ')) {
                inRenameBlock = true
            } else if (line.startsWith('rename to ')) {
                String renamedTo = unquote(line.substring('rename to '.length()))
                analyzer.touchedFiles << renamedTo
                currentFile = renamedTo
                inRenameBlock = false
            } else if (line.startsWith('copy from ') || line.startsWith('copy to ')) {
                if (line.startsWith('copy to ')) {
                    String copiedTo = unquote(line.substring('copy to '.length()))
                    analyzer.touchedFiles << copiedTo
                    currentFile = copiedTo
                }
            } else if (line.startsWith('@@')) {
                HunkHeader header = parseHunkHeader(line)
                lineNumber = header.start
                inHunk = true
            } else if (line.startsWith('new file mode') ||
                       line.startsWith('deleted file mode') ||
                       line.startsWith('old mode ') ||
                       line.startsWith('new mode ') ||
                       line.startsWith('similarity index') ||
                       line.startsWith('dissimilarity index')) {
                // metadata; ignore
            } else if (currentFile != null && !inRenameBlock) {
                if (!inHunk && isFileHeaderLine(line, '-')) {
                    // "--- a/path" or "--- /dev/null" before the first hunk
                } else if (!inHunk && isFileHeaderLine(line, '+')) {
                    // "+++ b/path" or "+++ /dev/null" before the first hunk
                } else if (isAddedContent(line)) {
                    analyzer.addedLines << new ChangedLine(currentFile, lineNumber)
                    lineNumber++
                } else if (isRemovedContent(line)) {
                    lineNumber
                } else if (line.startsWith(' ')) {
                    lineNumber++
                }
            }
        }
        return analyzer
    }

    boolean hasChanges() {
        return !addedLines.isEmpty() || !touchedFiles.isEmpty()
    }

    Set<String> touchedFiles() {
        return new LinkedHashSet<>(touchedFiles)
    }

    /**
     * Test-only accessor for the private tokenizer. Production code must
     * not call this directly; it exists so the tokenization contract can
     * be pinned by unit tests without driving it through the full parser.
     */
    static List<String> tokenizeGitArgsForTest(String input) {
        return tokenizeGitArgs(input)
    }

    /**
     * Returns true when the given (file, line) corresponds to a line that was
     * added in this PR. Removed lines and context lines return false on purpose.
     */
    boolean lineBelongsToChange(String file, int line) {
        if (!file) return false
        return addedLines.any { ChangedLine added -> added.file == file && added.line == line }
    }

    private static String[] extractGitDiffPaths(String header) {
        String body = header.substring('diff --git '.length())
        List<String> tokens = tokenizeGitArgs(body)
        if (tokens == null || tokens.size() != 2) return null
        // tokenizeGitArgs already decodes C-style escapes inside quoted
        // regions, so the tokens we get here are clean paths. No further
        // unquote/decode is needed; doing so would double-decode and eat
        // the next character after every backslash.
        String a = stripDirPrefix(tokens[0], 'a/')
        String b = stripDirPrefix(tokens[1], 'b/')
        return new String[] { a, b }
    }

    private static String stripDirPrefix(String path, String prefix) {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path
    }

    /**
     * Decodes a path token as it appears in {@code rename to} or {@code copy to}
     * lines. Git quotes paths containing special characters using C-style
     * escapes; this method reverses them so we keep a clean canonical path.
     *
     * <p>Idempotent: if the string is already unquoted (no surrounding
     * double quotes) it is returned verbatim. This matters because the
     * tokenizer used for {@code diff --git} headers already decodes
     * quoted paths; passing one of those tokens through this method a
     * second time must not corrupt it.</p>
     */
    private static String unquote(String raw) {
        if (!raw) return raw
        boolean quoted = raw.length() >= 2 &&
            raw.charAt(0) == '"' &&
            raw.charAt(raw.length() - 1) == '"'
        if (!quoted) {
            return raw
        }
        String inner = raw.substring(1, raw.length() - 1)
        // Reverse C-style escapes (\", \\, \nnn).
        StringBuilder out = new StringBuilder(inner.length())
        int i = 0
        while (i < inner.length()) {
            char c = inner.charAt(i)
            if (c == '\\' && i + 1 < inner.length()) {
                char next = inner.charAt(i + 1)
                if (next == '\\' || next == '"') {
                    out.append(next)
                    i += 2
                    continue
                }
                if (i + 3 < inner.length() &&
                    Character.isDigit(next) &&
                    Character.isDigit(inner.charAt(i + 2)) &&
                    Character.isDigit(inner.charAt(i + 3))) {
                    int code = Integer.parseInt(inner.substring(i + 1, i + 4), 8)
                    out.append((char) code)
                    i += 4
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * Tokenizes a git-style argument list (as found after {@code diff --git})
     * into decoded path tokens. The tokenizer and the C-style escape decoder
     * run together in a single state machine so that {@code \"}} inside a
     * quoted region cannot be mistaken for a closing quote.
     *
     * <p>Returns null if the input cannot be parsed into a well-formed
     * sequence of tokens (e.g. an unterminated quoted region or a trailing
     * escape at the end of the input).</p>
     */
    private static List<String> tokenizeGitArgs(String input) {
        List<String> tokens = new ArrayList<>(2)
        StringBuilder current = null
        boolean inQuotes = false
        boolean escaped = false
        int i = 0
        int len = input.length()

        while (i < len) {
            char c = input.charAt(i)

            if (escaped) {
                if (current == null) current = new StringBuilder()
                if (c == '\\' || c == '"') {
                    current.append(c)
                } else if (Character.isDigit(c) && i + 2 < len &&
                           Character.isDigit(input.charAt(i + 1)) &&
                           Character.isDigit(input.charAt(i + 2))) {
                    int code = Integer.parseInt(input.substring(i, i + 3), 8)
                    current.append((char) code)
                    i += 2
                } else {
                    current.append(c)
                }
                escaped = false
                i++
                continue
            }

            if (c == '\\') {
                escaped = true
                i++
                continue
            }

            if (inQuotes) {
                if (c == '"') {
                    inQuotes = false
                    i++
                    continue
                }
                if (current == null) current = new StringBuilder()
                current.append(c)
                i++
                continue
            }

            if (c == '"') {
                inQuotes = true
                i++
                continue
            }

            if (c == ' ' || c == '\t') {
                if (current != null) {
                    tokens.add(current.toString())
                    current = null
                }
                i++
                continue
            }

            if (current == null) current = new StringBuilder()
            current.append(c)
            i++
        }

        if (escaped || inQuotes) {
            return null
        }
        if (current != null) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /**
     * True when the line is a {@code +}-prefixed hunk content line. The
     * caller is responsible for ensuring we are inside a hunk (past the
     * file headers); from that point on every {@code +}-prefixed line is
     * content, including lines that happen to start with {@code +++}.
     */
    private static boolean isAddedContent(String line) {
        return line.startsWith('+')
    }

    /**
     * True when the line is a {@code -}-prefixed hunk content line. Same
     * rule as {@link #isAddedContent(String)}.
     */
    private static boolean isRemovedContent(String line) {
        return line.startsWith('-')
    }

    /**
     * Recognises the exact per-file header shapes {@code --- a/path},
     * {@code --- /dev/null}, {@code +++ b/path} and {@code +++ /dev/null}.
     * Other lines starting with '---' or '+++' (e.g. {@code ++++ b/foo}
     * inside a hunk) are not headers — they fail the 4th-character check.
     */
    private static boolean isFileHeaderLine(String line, String prefix) {
        if (!prefix || prefix.length() != 1) return false
        char p = prefix.charAt(0)
        if (line.length() < 4) return false
        if (line.charAt(0) != p) return false
        if (line.charAt(1) != p) return false
        if (line.charAt(2) != p) return false
        char fourth = line.charAt(3)
        return fourth == ' ' || fourth == '/'
    }

    private static HunkHeader parseHunkHeader(String line) {
        Matcher m = (line =~ /@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/)
        if (m.find()) {
            return new HunkHeader(start: Integer.parseInt(m.group(1)))
        }
        return new HunkHeader(start: 0)
    }

    static class HunkHeader {
        int start
    }

    /**
     * One added line, identified by its post-merge file path and line number.
     * Renamed from the previous {@code ChangedRange} to reflect that we only
     * model individual lines, not multi-line ranges.
     */
    static class ChangedLine {
        String file
        int line

        ChangedLine(String file, int line) {
            this.file = file
            this.line = line
        }
    }
}
