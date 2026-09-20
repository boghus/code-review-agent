package com.boghus.codereview.coverage

import groovy.transform.CompileStatic

@CompileStatic
class ChangedSourceLine {
    final String path
    final int lineNumber

    ChangedSourceLine(String path, int lineNumber) {
        this.path = path
        this.lineNumber = lineNumber
    }
}
