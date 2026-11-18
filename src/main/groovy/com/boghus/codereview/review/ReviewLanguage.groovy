package com.boghus.codereview.review

import groovy.transform.CompileStatic

@CompileStatic
enum ReviewLanguage {
    ENGLISH('en', 'English'),
    SPANISH('es', 'Spanish')

    final String code
    final String promptName

    ReviewLanguage(String code, String promptName) {
        this.code = code
        this.promptName = promptName
    }

    static ReviewLanguage fromCode(String code) {
        for (ReviewLanguage language : values()) {
            if (language.code == code) {
                return language
            }
        }
        return null
    }
}
