package com.boghus.minimax.model

import groovy.transform.CompileStatic

@CompileStatic
class ChatRequest {
    final String model
    final List<Message> messages
    final BigDecimal temperature
    final Integer maxCompletionTokens

    public ChatRequest(String model, List<Message> messages, BigDecimal temperature = 0.1G, Integer maxCompletionTokens = 4096) {
        if (!model?.trim()) throw new IllegalArgumentException('MiniMax model must not be blank.')
        if (!messages) throw new IllegalArgumentException('Chat messages must not be empty.')
        this.model = model
        this.messages = messages
        this.temperature = temperature
        this.maxCompletionTokens = maxCompletionTokens
    }
}
