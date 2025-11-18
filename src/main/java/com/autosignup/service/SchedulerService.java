package com.autosignup.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final OrchestratorService orchestratorService;

    @Value("${scheduler.interval.ms:300000}")
    private long schedulerIntervalMs;

    private final AtomicLong nextRunEpochMs = new AtomicLong();

    @PostConstruct
    public void initializeNextRunTime() {
        nextRunEpochMs.set(System.currentTimeMillis() + schedulerIntervalMs);
    }

    @Scheduled(fixedDelayString = "${scheduler.interval.ms:300000}")
    public void runScheduledCheck() {
        logger.info("Starting navigator sweep.");
        orchestratorService.runAllNavigators();
        nextRunEpochMs.set(System.currentTimeMillis() + schedulerIntervalMs);
        logger.info("Navigator sweep finished. Next run scheduled in {} seconds.",
                Duration.ofMillis(schedulerIntervalMs).toSeconds());
    }

    @Scheduled(fixedRate = 30000)
    public void logTimeUntilNextRun() {
        long remainingMillis = nextRunEpochMs.get() - System.currentTimeMillis();
        if (remainingMillis < 0) {
            remainingMillis = 0;
        }
        logger.info("Next navigator sweep in {} seconds.", Duration.ofMillis(remainingMillis).toSeconds());
    }
}