package com.boghus.codereview.coverage

import groovy.transform.CompileStatic

@CompileStatic
class SourcePathMapper {
    String normalize(String path) {
        if (!path) return ''
        String normalized = path.replace('\\', '/')
        if (normalized.startsWith('src/main/groovy/')) {
            normalized = normalized.substring('src/main/groovy/'.length())
        } else if (normalized.startsWith('src/main/java/')) {
            normalized = normalized.substring('src/main/java/'.length())
        }
        if (normalized.startsWith('./')) {
            normalized = normalized.substring(2)
        }
        return normalized
    }

    boolean matches(String gitPath, String jacocoPath) {
        normalize(gitPath) == normalize(jacocoPath)
    }
}
