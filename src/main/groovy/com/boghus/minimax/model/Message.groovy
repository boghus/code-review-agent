package com.boghus.minimax.model

import groovy.transform.CompileStatic

@CompileStatic
class Message {
    final String role
    final String content

    public Message(String role, String content) {
        if (!role?.trim()) throw new IllegalArgumentException('Message role must not be blank.')
        if (content == null) throw new IllegalArgumentException('Message content must not be null.')
        this.role = role
        this.content = content
    }
}
