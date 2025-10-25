package com.flipfinder.component;

import com.flipfinder.service.BulkIngestService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BulkIngestPoller {

    private final BulkIngestService service;

    public BulkIngestPoller(BulkIngestService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 600000)
    public void poll() {
        service.fetchAndProcess();  // delegate the actual work
    }
}
