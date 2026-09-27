package com.flipfinder.controller;

import com.flipfinder.dto.ItemHistoryDto;
import com.flipfinder.service.PriceHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "flipfinder.history.enabled", havingValue = "true")
@Tag(name = "History", description = "How an item's latest trading compares with its history (homelab only)")
public class HistoryController {
    private final PriceHistoryService history;

    public HistoryController(PriceHistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/items/{id}/history")
    @Operation(summary = "Compare an item's latest trading with its history",
            description = "Whether the latest five-minute volume, price and margin are typical, compared with the "
                    + "stored five-minute and hourly history.")
    public ItemHistoryDto history(@PathVariable int id) {
        return history.summarize(id, Instant.now().getEpochSecond())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown item " + id));
    }
}
