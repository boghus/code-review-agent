package com.boghus.codereview.provider

import groovy.transform.CompileStatic

import java.util.regex.Matcher

/**
 * Sanitizes exception text before it is written to a runner log.
 *
 * Exception messages are treated as untrusted data. Common credential and
 * token values are redacted while exception type and surrounding diagnostic
 * context are preserved. This utility is independent from provider-specific
 * error translation and trusted-rules handling.
 */
@CompileStatic
class RuntimeErrorSanitizer {

    private static final List<String> SENSITIVE_PATTERNS = [
        '(?i)(api[-_ ]?key|access[-_ ]?token|refresh[-_ ]?token|token|password|secret|credential)\\s*([=:]\\s*)("[^"]*"|\\'[^\\']*\\'|[^\\s,;]+)',
        '(?i)(authorization\\s*:\\s*bearer)\\s+[^\\s,;]+'
    ]

    static String sanitize(Throwable exception) {
        if (exception == null) {
            return 'unknown error'
        }

        List<String> messages = []
        Throwable current = exception
        int depth = 0
        while (current != null && depth < 5) {
            String message = current.message?.trim()
            if (message) {
                messages << "${current.class.simpleName}: ${sanitize(message)}".toString()
            } else {
                messages << current.class.simpleName
            }
            current = current.cause
            depth++
        }

        messages.join(' -> ')
    }

    static String sanitize(String message) {
        if (!message) {
            return ''
        }

        String sanitized = message.replace('`', "'")
        SENSITIVE_PATTERNS.each { String pattern ->
            Matcher matcher = sanitized =~ pattern
            StringBuffer result = new StringBuffer()
            while (matcher.find()) {
                if (matcher.groupCount() == 3) {
                    String replacement = "${matcher.group(1)}${matcher.group(2)}[REDACTED]"
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
                } else {
                    String replacement = "${matcher.group(1)} [REDACTED]"
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
                }
            }
            matcher.appendTail(result)
            sanitized = result.toString()
        }
        sanitized
    }
}
