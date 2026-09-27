package com.flipfinder.component;

import com.flipfinder.service.PriceHistoryService;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flipfinder.history.enabled", havingValue = "true")
public class PriceHistoryPoller {

    private final PriceHistoryService service;

    public PriceHistoryPoller(PriceHistoryService service) {
        this.service = service;
    }

    // The first run fetches recent history within seconds; later runs backfill the rest a few buckets at a time.
    @Scheduled(initialDelayString = "${flipfinder.history.initial-delay-ms:3000}",
            fixedDelayString = "${flipfinder.history.poll-delay-ms:15000}")
    public void poll() throws InterruptedException {
        service.catchUp(Instant.now().getEpochSecond());
    }
}
