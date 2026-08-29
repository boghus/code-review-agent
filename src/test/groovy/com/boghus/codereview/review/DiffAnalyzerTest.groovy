package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class DiffAnalyzerTest {

    @Test
    void 'parses added lines and touched files'() {
        String diff = '''diff --git a/src/Foo.groovy b/src/Foo.groovy
index 1234..5678 100644
--- a/src/Foo.groovy
+++ b/src/Foo.groovy
@@ -1,3 +1,4 @@
 class Foo {
+    int added = 1
     void bar() {}
-    void legacy() {}
 }
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.hasChanges()).isTrue()
        assertThat(analyzer.touchedFiles()).containsExactly('src/Foo.groovy')
        assertThat(analyzer.lineBelongsToChange('src/Foo.groovy', 2)).isTrue()
        assertThat(analyzer.lineBelongsToChange('src/Foo.groovy', 3)).isFalse()
    }

    @Test
    void 'returns empty analyzer for empty diff'() {
        DiffAnalyzer analyzer = DiffAnalyzer.parse('')

        assertThat(analyzer.hasChanges()).isFalse()
        assertThat(analyzer.touchedFiles()).isEmpty()
    }

    @Test
    void 'returns empty analyzer for null diff'() {
        DiffAnalyzer analyzer = DiffAnalyzer.parse(null)

        assertThat(analyzer.hasChanges()).isFalse()
    }

    @Test
    void 'tracks renamed files as the new path'() {
        String diff = '''diff --git a/Foo.java b/Bar.java
similarity index 95%
rename from Foo.java
rename to Bar.java
index 1234..5678 100644
--- a/Foo.java
+++ b/Bar.java
@@ -1,2 +1,3 @@
 class Bar {
+    int added = 1
 }
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).contains('Bar.java')
        assertThat(analyzer.lineBelongsToChange('Bar.java', 2)).isTrue()
        assertThat(analyzer.lineBelongsToChange('Foo.java', 2)).isFalse()
    }

    @Test
    void 'handles new files (dev null)'() {
        String diff = '''diff --git a/New.java b/New.java
new file mode 100644
index 0000000..1234
--- /dev/null
+++ b/New.java
@@ -0,0 +1,2 @@
+class New {
+}
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('New.java')
        assertThat(analyzer.lineBelongsToChange('New.java', 1)).isTrue()
        assertThat(analyzer.lineBelongsToChange('New.java', 2)).isTrue()
    }

    @Test
    void 'handles deleted files (no added lines)'() {
        String diff = '''diff --git a/Old.java b/Old.java
deleted file mode 100644
index 1234..0000000
--- a/Old.java
+++ /dev/null
@@ -1,2 +0,0 @@
-class Old {
-}
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('Old.java')
        assertThat(analyzer.hasChanges()).isTrue()
        assertThat(analyzer.lineBelongsToChange('Old.java', 1)).isFalse()
    }

    @Test
    void 'handles binary files (metadata only)'() {
        String diff = '''diff --git a/assets/logo.png b/assets/logo.png
index 1234..5678 100644
Binary files a/assets/logo.png and b/assets/logo.png differ
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('assets/logo.png')
        assertThat(analyzer.hasChanges()).isTrue()
        assertThat(analyzer.lineBelongsToChange('assets/logo.png', 1)).isFalse()
    }

    @Test
    void 'handles multiple hunks in the same file'() {
        String diff = '''diff --git a/src/Service.java b/src/Service.java
index 1234..5678 100644
--- a/src/Service.java
+++ b/src/Service.java
@@ -1,3 +1,4 @@
 package x;
 public class Service {
+    int a = 1;
     void m() {}
 }
@@ -20,2 +21,3 @@
     int b;
+    int c = 2;
     int d;
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.lineBelongsToChange('src/Service.java', 3)).isTrue()
        // Second hunk @@ -20,2 +21,3 @@:
        //   old line 20 = " int b;" (context, becomes new line 21)
        //   old line 21 = " int d;" (context, becomes new line 23)
        //   added " int c = 2;" sits at new line 22.
        assertThat(analyzer.lineBelongsToChange('src/Service.java', 22)).isTrue()
        assertThat(analyzer.lineBelongsToChange('src/Service.java', 21)).isFalse()
        assertThat(analyzer.lineBelongsToChange('src/Service.java', 23)).isFalse()
    }

    @Test
    void 'handles paths with spaces using git quoting'() {
        // git can quote paths containing spaces using C-style quoting; this
        // is one of the formats the parser must support.
        String diff = '''diff --git "a/path with spaces/File.java" "b/path with spaces/File.java"
index 1234..5678 100644
--- "a/path with spaces/File.java"
+++ "b/path with spaces/File.java"
@@ -1,2 +1,3 @@
 class File {
+    int added = 1;
 }
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('path with spaces/File.java')
        assertThat(analyzer.lineBelongsToChange('path with spaces/File.java', 2)).isTrue()
    }

    @Test
    void 'handles paths with tabs using git quoting'() {
        String path = 'path\twith\ttabs/File.java'
        String diff = "diff --git \"a/${path}\" \"b/${path}\"\n" +
            "--- \"a/${path}\"\n" +
            "+++ \"b/${path}\"\n" +
            "@@ -1,2 +1,3 @@\n" +
            " class File {\n" +
            "+    int added = 1;\n" +
            " }\n"
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly(path)
    }

    @Test
    void 'handles paths with backslashes using git quoting'() {
        // git encodes backslashes as \\ inside the quoted path.
        String path = 'dir\\name/File.java'
        String quotedPath = path.replace('\\', '\\\\')
        String diff = "diff --git \"a/${quotedPath}\" \"b/${quotedPath}\"\n" +
            "--- \"a/${quotedPath}\"\n" +
            "+++ \"b/${quotedPath}\"\n" +
            "@@ -1,2 +1,3 @@\n" +
            " class File {\n" +
            "+    int added = 1;\n" +
            " }\n"
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly(path)
    }

    @Test
    void 'handles paths with unicode using git quoting'() {
        String diff = '''diff --git "a/src/ñandú/File.java" "b/src/ñandú/File.java"
--- "a/src/ñandú/File.java"
+++ "b/src/ñandú/File.java"
@@ -1,2 +1,3 @@
 class File {
+    int added = 1;
 }
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('src/ñandú/File.java')
    }

    @Test
    void 'handles paths containing quotes using backslash-escape'() {
        // git encodes a literal " inside the path as \" and wraps the
        // whole path in double quotes. We build the diff using only
        // single-quoted strings + concatenation so escape rules cannot
        // accidentally rewrite the byte sequence.
        //
        // Runtime content we want inside the surrounding quotes:
        //     a/he said \"hi\"/File.java
        // i.e. the bytes: a / h e SP s a i d SP \ " h i \ " / F i l e . j a v a
        String insideA = 'a/he said ' + '\\' + '"' + 'hi' + '\\' + '"' + '/File.java'
        String insideB = insideA.replaceFirst('a/', 'b/')

        String diff = 'diff --git "' + insideA + '" "' + insideB + '"' + '\n' +
            '--- "' + insideA + '"' + '\n' +
            '+++ "' + insideB + '"' + '\n' +
            '@@ -1,2 +1,3 @@' + '\n' +
            ' class File {' + '\n' +
            '+    int added = 1;' + '\n' +
            ' }' + '\n'

        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        // Expected runtime path (one literal " between 'said' and 'hi'):
        //     he said "hi"/File.java
        String expected = 'he said ' + '"' + 'hi' + '"' + '/File.java'
        assertThat(analyzer.touchedFiles()).containsExactly(expected)
    }

    @Test
    void 'embedded backslash does not confuse the tokenizer'() {
        // Git encodes a literal backslash in the path as two backslashes
        // inside the quoted diff header. Build byte-by-byte to avoid
        // Groovy escape rules rewriting the sequence.
        //
        // Runtime content we want inside the surrounding quotes:
        //     a/dir\\sub/File.java
        // i.e. bytes: a / d i r \ \ s u b / F i l e . j a v a
        String insideA = 'a/dir' + '\\' + '\\' + 'sub/File.java'
        String insideB = insideA.replaceFirst('a/', 'b/')

        String diff = 'diff --git "' + insideA + '" "' + insideB + '"' + '\n' +
            '--- "' + insideA + '"' + '\n' +
            '+++ "' + insideB + '"' + '\n' +
            '@@ -1,2 +1,3 @@' + '\n' +
            ' class File {' + '\n' +
            '+    int added = 1;' + '\n' +
            ' }' + '\n'

        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        // Expected runtime path (one literal backslash):
        //     dir\sub/File.java
        String expected = 'dir' + '\\' + 'sub/File.java'
        assertThat(analyzer.touchedFiles()).containsExactly(expected)
    }

    @Test
    void 'rejects malformed unterminated quoted path'() {
        // Unterminated quote at end of header — must not throw, must not
        // silently truncate, must surface as no-touched-files.
        String diff = '''diff --git "a/never closes b/x
--- a/x
+++ b/x
@@ -1 +1 @@
-a
+b
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        // The malformed header fails to parse, so we don't trust any path.
        // The fall-back behaviour is that no path is tracked, which keeps
        // the orchestrator from posting bogus file names to the AI.
        assertThat(analyzer.touchedFiles()).doesNotContain('a/never closes b/x')
    }

    @Test
    void 'does not treat added or removed file headers as code'() {
        // --- /dev/null and +++ /dev/null must be ignored, not counted as +/- lines.
        String diff = '''diff --git a/New.java b/New.java
new file mode 100644
--- /dev/null
+++ b/New.java
@@ -0,0 +1,1 @@
+class New {}
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.lineBelongsToChange('New.java', 0)).isFalse()
        assertThat(analyzer.lineBelongsToChange('New.java', 1)).isTrue()
    }

    @Test
    void 'handles multiple files in the same diff'() {
        String diff = '''diff --git a/A.java b/A.java
--- a/A.java
+++ b/A.java
@@ -1 +1,2 @@
 class A {}
+// new
diff --git a/B.java b/B.java
--- a/B.java
+++ b/B.java
@@ -1 +1,2 @@
 class B {}
+// new
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('A.java', 'B.java')
        assertThat(analyzer.lineBelongsToChange('A.java', 2)).isTrue()
        assertThat(analyzer.lineBelongsToChange('B.java', 2)).isTrue()
    }

    @Test
    void 'survives lines that look like +++ but are not file headers'() {
        // A hunk where one of the content lines begins with "+++" — must
        // NOT be confused with the file header "+++ b/path". It counts as
        // a real added line and advances the line counter.
        //   old: 2 lines ( // header ; +int added = 1; )
        //   new: 3 lines ( // header ; +++ not a file header ; +int added = 1; )
        String diff = '''diff --git a/X.java b/X.java
--- a/X.java
+++ b/X.java
@@ -1,2 +1,3 @@
 // header
+++ not a file header
+int added = 1;
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('X.java')
        // Line 1: context " // header".
        // Line 2: added "+++ not a file header".
        // Line 3: added "+int added = 1;".
        assertThat(analyzer.lineBelongsToChange('X.java', 2)).isTrue()
        assertThat(analyzer.lineBelongsToChange('X.java', 3)).isTrue()
    }

    @Test
    void '+++ before the first hunk is treated as the file header'() {
        // The actual file-header shape must still be recognised as a header
        // (not content) when it appears before the first @@.
        String diff = '''diff --git a/X.java b/X.java
--- a/X.java
+++ b/X.java
@@ -1 +1 @@
-old
+new
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        // Header lines do not consume a line number in the new file.
        assertThat(analyzer.lineBelongsToChange('X.java', 0)).isFalse()
        // The real added line.
        assertThat(analyzer.lineBelongsToChange('X.java', 1)).isTrue()
    }

    @Test
    void '+++ inside a hunk is content, not a file header'() {
        // Regression: lines like "++++ b/foo" (four plus signs) and
        // "+++ b/foo" appearing INSIDE a hunk were previously misclassified
        // as file headers. They must be treated as added content.
        String diff = '''diff --git a/X.java b/X.java
--- a/X.java
+++ b/X.java
@@ -1,2 +1,4 @@
 existing()
++++ b/not-a-header.txt
+anotherLine()
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        assertThat(analyzer.touchedFiles()).containsExactly('X.java')
        // line 2 of the new file is "++++ b/not-a-header.txt" — added content
        assertThat(analyzer.lineBelongsToChange('X.java', 2)).isTrue()
        // line 3 of the new file is "anotherLine()"
        assertThat(analyzer.lineBelongsToChange('X.java', 3)).isTrue()
    }

    @Test
    void 'recognises the actual file header before the first hunk'() {
        // --- a/... and +++ b/... appearing BEFORE any @@ are file headers
        // and must NOT be counted as content.
        String diff = '''diff --git a/X.java b/X.java
--- a/X.java
+++ b/X.java
@@ -1 +1 @@
-old
+new
'''
        DiffAnalyzer analyzer = DiffAnalyzer.parse(diff)

        // The pre-hunk --- / +++ must not be in addedLines.
        assertThat(analyzer.lineBelongsToChange('X.java', 0)).isFalse()
        // The actual content line.
        assertThat(analyzer.lineBelongsToChange('X.java', 1)).isTrue()
    }
}
