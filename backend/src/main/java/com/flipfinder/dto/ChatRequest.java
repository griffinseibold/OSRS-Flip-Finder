package com.flipfinder.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public class ChatRequest {
    @Schema(description = "Account hash from the RuneLite plugin to answer for, or null")
    public Long account;

    @Schema(description = "The conversation so far, oldest first, ending with the player's message")
    public List<Message> messages = List.of();

    public static class Message {
        @Schema(description = "user or assistant")
        public String role;

        public String content;
    }
}
