package com.flipfinder.service;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BulkIngestService {
    private static final String URL = "https://chisel.weirdgloop.org/gazproj/gazbot/os_dump.json";
    private static final int BATCH_SIZE = 500;
    private final JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private List<Object[]> batch;

    public BulkIngestService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.batch = new ArrayList<>(BATCH_SIZE);
    }

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
                batch = new ArrayList<>(BATCH_SIZE);
                int count = 0;
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String id = p.currentName();
                    p.nextToken();
                    JsonNode node = mapper.readTree(p);
                    String nowIso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    handleRecord(id, node, nowIso);
                    count++;
                    if (batch.size() == BATCH_SIZE) {
                        flushBatch(batch);
                    }
                }
                System.out.println("Processed " + count + " records");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void flushBatch(List<Object[]> batch) {
        // Only update if incoming source_updated_at is newer than existing
        final String sql = """
                    INSERT INTO records (id, data, source_updated_at, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                      data = excluded.data,
                      source_updated_at = excluded.source_updated_at,
                      updated_at = excluded.updated_at
                    WHERE datetime(coalesce(records.source_updated_at, '0001-01-01T00:00:00Z'))
                          < datetime(excluded.source_updated_at);
                """;
        jdbc.batchUpdate(sql, batch);
        batch.clear();
    }

    private void handleRecord(String id, JsonNode node, String nowIso) {
        // TODO: Implement processing logic here
        if (node == null || node.isEmpty()) {
            return;
        }
        String srcUpdated = node.hasNonNull("lastUpdated")
                ? node.get("lastUpdated").asText()
                : nowIso;
        batch.add(new Object[] {
                Integer.parseInt(id), node.toString(), srcUpdated, nowIso
        });
        System.out.println("Processing record ID " + id);
    }
}
