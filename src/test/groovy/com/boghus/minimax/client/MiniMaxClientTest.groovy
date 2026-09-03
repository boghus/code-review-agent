package com.boghus.minimax.client

import com.boghus.minimax.exception.MiniMaxException
import com.boghus.minimax.model.ChatRequest
import com.boghus.minimax.model.ChatResponse
import com.boghus.minimax.model.Message
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.net.InetSocketAddress

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class MiniMaxClientTest {
    HttpServer server
    String requestBody
    String authorization

    @BeforeEach
    void setUp() {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/v1/chat/completions') { exchange ->
            requestBody = exchange.requestBody.text
            authorization = exchange.requestHeaders.getFirst('Authorization')
            byte[] response = '''{
                "id":"test-id",
                "model":"MiniMax-M3",
                "choices":[{"message":{"role":"assistant","content":"review result"},"finish_reason":"stop"}],
                "base_resp":{"status_code":0,"status_msg":""}
            }'''.bytes
            exchange.responseHeaders.set('Content-Type', 'application/json')
            exchange.sendResponseHeaders(200, response.length)
            exchange.responseBody.write(response)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    void tearDown() {
        server?.stop(0)
    }

    @Test
    void 'sends chat request and parses assistant content'() {
        MiniMaxClient client = new MiniMaxClient('test-key', "http://localhost:${server.address.port}/v1")
        ChatResponse response = client.chat(new ChatRequest('MiniMax-M3', [new Message('user', 'Review this code')]))

        assertThat(response.content).isEqualTo('review result')
        assertThat(response.model).isEqualTo('MiniMax-M3')
        assertThat(authorization).isEqualTo('Bearer test-key')
        assertThat(requestBody).contains('"model":"MiniMax-M3"')
        assertThat(requestBody).contains('"content":"Review this code"')
    }

    @Test
    void 'rejects blank api key'() {
        assertThatThrownBy { new MiniMaxClient(' ') }
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining('API key')
    }

    @Test
    void 'surfaces provider error status'() {
        server.removeContext('/v1/chat/completions')
        server.createContext('/v1/chat/completions') { exchange ->
            byte[] response = '{"base_resp":{"status_code":2049,"status_msg":"invalid api key"}}'.bytes
            exchange.sendResponseHeaders(200, response.length)
            exchange.responseBody.write(response)
            exchange.close()
        }

        MiniMaxClient client = new MiniMaxClient('bad-key', "http://localhost:${server.address.port}/v1")

        assertThatThrownBy { client.chat(new ChatRequest('MiniMax-M3', [new Message('user', 'hello')])) }
            .isInstanceOf(MiniMaxException.class)
            .extracting('statusCode')
            .isEqualTo(2049)
    }
}
