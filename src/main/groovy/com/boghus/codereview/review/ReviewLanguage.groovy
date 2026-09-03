package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
enum ReviewLanguage {
    ENGLISH('English'),
    SPANISH('Spanish')

    final String promptName

    ReviewLanguage(String promptName) {
        this.promptName = promptName
    }
}
