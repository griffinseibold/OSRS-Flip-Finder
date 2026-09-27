package com.flipfinder.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BulkIngestServiceTests {
    private static final String BASE_URL = "https://prices.runescape.wiki/api/v2/osrs";

    private final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode mapping = json("""
            [
              {
                "id": 10,
                "name": "Cannon barrels",
                "examine": "The barrels of the multicannon.",
                "members": true,
                "lowalch": 75000,
                "highalch": 112500,
                "limit": 70,
                "value": 187500,
                "icon": "Cannon barrels.png"
              },
              {
                "id": 2,
                "name": "Steel cannonball",
                "members": true
              }
            ]
            """);
    private final JsonNode latest = json("""
            {
              "data": {
                "10": {"high": 164136, "highTime": 1790522991, "low": 162000, "lowTime": 1790522975}
              }
            }
            """);
    private final JsonNode fiveMinute = json("""
            {
              "timestamp": 1790522700,
              "data": {
                "10": {
                  "avgHighPrice": 164100.5,
                  "highPriceVolume": 14,
                  "avgLowPrice": 162000,
                  "lowPriceVolume": 9
                }
              }
            }
            """);

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void importsAllThreeBulkResponsesAndFlushesPartialBatches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BulkIngestService service = new BulkIngestService(
                jdbc, TransactionOperations.withoutTransaction(), BASE_URL, "test-agent");

        BulkIngestService.IngestResult result = service.processResponses(mapping, latest, fiveMinute);

        ArgumentCaptor<List> batches = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(3)).batchUpdate(anyString(), batches.capture());
        List<List> captured = batches.getAllValues();

        assertThat(result.mappedItems()).isEqualTo(2);
        assertThat(result.latestPrices()).isEqualTo(1);
        assertThat(result.fiveMinutePrices()).isEqualTo(1);
        assertThat(captured.get(0)).hasSize(2);
        assertThat((Object[]) captured.get(0).get(0))
                .containsSequence(10, "Cannon barrels", "The barrels of the multicannon.", 1);
        assertThat((Object[]) captured.get(1).get(0))
                .containsSequence(164136L, 1790522991L, 162000L, 1790522975L);
        assertThat((Object[]) captured.get(2).get(0))
                .containsSequence(164100.5, 14L, 162000.0, 9L, 1790522700L);
        verify(jdbc).update(anyString(), eq(1790522700L), anyString());
    }

    @Test
    void writesTheWholeImportInOneTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        BulkIngestService service = new BulkIngestService(
                jdbc, new TransactionTemplate(transactionManager), BASE_URL, "test-agent");

        service.processResponses(mapping, latest, fiveMinute);

        InOrder order = inOrder(transactionManager, jdbc);
        order.verify(transactionManager).getTransaction(any());
        order.verify(jdbc, times(3)).batchUpdate(anyString(), anyList());
        order.verify(transactionManager).commit(any());
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, never()).rollback(any());
    }

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
