package com.deepai.mcpclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration for scheduled tasks and background monitoring
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfiguration.class);

    /**
     * Configure task scheduler for health monitoring and background tasks
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("health-monitor-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);

        logger.info("📅 Task scheduler configured for health monitoring");
        return scheduler;
    }
}