package com.flipfinder.service;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Service;

@Service
public class BulkIngestService {
    private static final String URL = "https://chisel.weirdgloop.org/gazproj/gazbot/os_dump.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public void fetchAndProcess() {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(URL)).GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + resp.statusCode());
            }

            try (JsonParser p = mapper.getFactory().createParser(resp.body())) {
                if (p.nextToken() != JsonToken.START_OBJECT) {
                    throw new IllegalStateException("Expected root object");
                }
                int count = 0;
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String id = p.currentName();
                    p.nextToken();
                    JsonNode node = mapper.readTree(p);
                    handleRecord(id, node);
                    count++;
                }
                System.out.println("Processed " + count + " records");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRecord(String id, JsonNode node) {
        // TODO: Implement processing logic here
        System.out.println("Processing record ID " + id);
    }
}
