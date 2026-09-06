package com.boghus.minimax.exception

import groovy.transform.CompileStatic

@CompileStatic
class MiniMaxException extends RuntimeException {
    final int statusCode

    MiniMaxException(String message, int statusCode = 0, Throwable cause = null) {
        super(message, cause)
        this.statusCode = statusCode
    }
}
