package com.boghus.codereview.coverage

import groovy.transform.CompileStatic

@CompileStatic
class JaCoCoSourceLine {
    final String sourcePath
    final int lineNumber
    final boolean executable
    final boolean covered

    JaCoCoSourceLine(String sourcePath, int lineNumber, boolean executable, boolean covered) {
        this.sourcePath = sourcePath
        this.lineNumber = lineNumber
        this.executable = executable
        this.covered = covered
    }
}
