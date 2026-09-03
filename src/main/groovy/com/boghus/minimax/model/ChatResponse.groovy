package com.boghus.minimax.model

import groovy.transform.CompileStatic

@CompileStatic
class ChatResponse {
    final String content
    final String model

    ChatResponse(String content, String model) {
        this.content = content
        this.model = model
    }
}
