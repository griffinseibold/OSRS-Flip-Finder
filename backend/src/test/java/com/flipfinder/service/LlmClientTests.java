package com.flipfinder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LlmClientTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    /** Starts a server that answers /v1/chat/completions with the given status and event-stream lines. */
    private LlmClient serve(int status, String... events) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String event : events) {
                    out.write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        });
        server.start();
        return new LlmClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsTextAsItArrivesAndReturnsTheWholeReply() throws Exception {
        LlmClient client = serve(200,
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"Buy \"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"gold leaf.\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "[DONE]");
        List<String> pieces = new ArrayList<>();

        LlmClient.Reply reply = client.complete(List.of(LlmClient.message("user", "What should I flip?")),
                List.of(), pieces::add);

        assertThat(pieces).containsExactly("Buy ", "gold leaf.");
        assertThat(reply.content()).isEqualTo("Buy gold leaf.");
        assertThat(reply.toolCalls()).isEmpty();
        assertThat(reply.finishReason()).isEqualTo("stop");
    }

    @Test
    void assemblesToolCallsFromTheirPieces() throws Exception {
        LlmClient client = serve(200,
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"find_flips\",\"arguments\":\"\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"sort\\\":\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"roi\\\"}\"}}]}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                "[DONE]");

        LlmClient.Reply reply = client.complete(List.of(LlmClient.message("user", "Best ROI?")),
                ChatService.TOOLS, text -> { });

        assertThat(reply.toolCalls()).containsExactly(new LlmClient.ToolCall("call-1", "find_flips", "{\"sort\":\"roi\"}"));
        assertThat(reply.finishReason()).isEqualTo("tool_calls");
    }

    @Test
    void streamsWithThinkingOffAndOffersTheTools() throws Exception {
        LlmClient client = serve(200, "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}", "[DONE]");

        client.complete(List.of(LlmClient.message("user", "Hi")), ChatService.TOOLS, text -> { });

        JsonNode body = mapper.readTree(requestBody.get());
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true)).isFalse();
        assertThat(body.path("tools").get(0).path("function").path("name").asText()).isEqualTo("find_flips");
        assertThat(body.has("model")).isFalse();
    }

    @Test
    void failsWhenTheServerReturnsAnError() throws Exception {
        LlmClient client = serve(503);

        assertThatThrownBy(() -> client.complete(List.of(LlmClient.message("user", "Hi")), List.of(), text -> { }))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("503");
    }
}
