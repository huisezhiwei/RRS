package com.rrs.config;

import com.rrs.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AiAgentService aiAgentService;

    @Override
    public void run(String... args) {
        log.info("Initializing built-in data...");
        aiAgentService.initBuiltInAgents();
        log.info("Data initialization complete.");
    }
}
