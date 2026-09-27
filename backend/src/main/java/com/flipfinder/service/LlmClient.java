package com.flipfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Streams chat completions from an OpenAI-compatible server, such as the
 * homelab's llama.cpp.
 */
@Component
@ConditionalOnProperty(name = "flipfinder.llm.base-url")
public class LlmClient {
    private final ObjectMapper mapper;
    private final URI completionsUri;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LlmClient(
            ObjectMapper mapper,
            @Value("${flipfinder.llm.base-url}") String baseUrl,
            @Value("${flipfinder.llm.model:}") String model) {
        this.mapper = mapper;
        this.completionsUri = URI.create(baseUrl.replaceFirst("/+$", "") + "/chat/completions");
        this.model = model;
    }

    /**
     * Sends the conversation and streams the reply, passing each piece of text
     * to {@code onText} as it arrives. Returns the whole reply, including any
     * tool calls the model made instead of answering.
     */
    public Reply complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, Consumer<String> onText)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!model.isBlank()) {
            body.put("model", model);
        }
        body.put("messages", messages);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("stream", true);
        body.put("temperature", 0.3);
        body.put("max_tokens", 800);
        // Qwen3 reasons at length before answering unless told not to.
        body.put("chat_template_kwargs", Map.of("enable_thinking", false));

        HttpRequest request = HttpRequest.newBuilder(completionsUri)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());

        try (Stream<String> lines = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("The language model returned HTTP " + response.statusCode());
            }
            StringBuilder content = new StringBuilder();
            Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();
            String finishReason = null;
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) {
                    break;
                }
                JsonNode chunk = mapper.readTree(data);
                if (chunk.has("error")) {
                    throw new IOException("The language model failed: " + chunk.get("error"));
                }
                JsonNode choice = chunk.path("choices").path(0);
                JsonNode delta = choice.path("delta");
                String text = delta.path("content").asText("");
                if (!text.isEmpty()) {
                    content.append(text);
                    onText.accept(text);
                }
                for (JsonNode call : delta.path("tool_calls")) {
                    toolCalls.computeIfAbsent(call.path("index").asInt(), ToolCallBuilder::new).add(call);
                }
                if (choice.hasNonNull("finish_reason")) {
                    finishReason = choice.get("finish_reason").asText();
                }
            }
            return new Reply(content.toString(), toolCalls.values().stream().map(ToolCallBuilder::build).toList(),
                    finishReason);
        }
    }

    public record ToolCall(String id, String name, String arguments) {
    }

    public record Reply(String content, List<ToolCall> toolCalls, String finishReason) {
    }

    /** Tool calls arrive in pieces: an id and name, then the arguments a few characters at a time. */
    private static final class ToolCallBuilder {
        private final int index;
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        ToolCallBuilder(int index) {
            this.index = index;
        }

        void add(JsonNode delta) {
            if (delta.hasNonNull("id")) {
                id = delta.get("id").asText();
            }
            JsonNode function = delta.path("function");
            if (function.hasNonNull("name")) {
                name = function.get("name").asText();
            }
            arguments.append(function.path("arguments").asText(""));
        }

        ToolCall build() {
            // Some servers leave out the id; the reply only needs to match it.
            return new ToolCall(id != null ? id : "call-" + index, name, arguments.isEmpty() ? "{}" : arguments.toString());
        }
    }

    /** Builds the message objects the API expects. */
    static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    static Map<String, Object> assistantToolCalls(String content, List<ToolCall> calls) {
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (ToolCall call : calls) {
            toolCalls.add(Map.of(
                    "id", call.id(),
                    "type", "function",
                    "function", Map.of("name", call.name(), "arguments", call.arguments())));
        }
        Map<String, Object> message = message("assistant", content);
        message.put("tool_calls", toolCalls);
        return message;
    }

    static Map<String, Object> toolResult(String toolCallId, String content) {
        Map<String, Object> message = message("tool", content);
        message.put("tool_call_id", toolCallId);
        return message;
    }
}
