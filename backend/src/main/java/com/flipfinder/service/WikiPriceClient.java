package com.flipfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads the RuneScape Wiki's real-time prices API, which asks every client to identify itself. */
@Component
public class WikiPriceClient {
    private final String baseUrl;
    private final String userAgent;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public WikiPriceClient(
            @Value("${flipfinder.ingest.base-url}") String baseUrl,
            @Value("${flipfinder.ingest.user-agent}") String userAgent) {
        this.baseUrl = baseUrl.replaceFirst("/+$", "");
        this.userAgent = userAgent;
    }

    public JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(path + " returned HTTP " + response.statusCode());
            }
            return mapper.readTree(body);
        }
    }
}
