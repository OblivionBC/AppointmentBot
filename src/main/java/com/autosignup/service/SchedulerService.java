package com.autosignup.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final OrchestratorService orchestratorService;

    @Scheduled(fixedDelayString = "${scheduler.interval.ms:300000}")
    public void runScheduledCheck() {
        orchestratorService.runAllNavigators();
    }
}