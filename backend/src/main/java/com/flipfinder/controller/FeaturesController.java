package com.flipfinder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the web app which homelab features this server has turned on. */
@RestController
@Tag(name = "Features", description = "Which optional features this server provides")
public class FeaturesController {
	private final Map<String, Boolean> features;

	public FeaturesController(
			@Value("${flipfinder.runelite.enabled:false}") boolean runelite,
			@Value("${flipfinder.history.enabled:false}") boolean history,
			@Value("${flipfinder.llm.base-url:}") String llmBaseUrl) {
		this.features = Map.of("runelite", runelite, "history", history, "chat", !llmBaseUrl.isBlank());
	}

	@GetMapping("/api/features")
	@Operation(summary = "List features", description = "runelite: accepts RuneLite plugin data; history: keeps "
			+ "trading history; chat: can answer questions with the homelab's language model.")
	public Map<String, Boolean> features() {
		return features;
	}
}
