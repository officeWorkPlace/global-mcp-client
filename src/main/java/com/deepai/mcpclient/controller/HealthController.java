package com.deepai.mcpclient.controller;

import com.deepai.mcpclient.monitoring.HealthMonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health monitoring endpoint for operational visibility
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthMonitoringService healthMonitoringService;

    @Autowired
    public HealthController(HealthMonitoringService healthMonitoringService) {
        this.healthMonitoringService = healthMonitoringService;
    }

    /**
     * Get monitoring statistics and current system status
     */
    @GetMapping("/monitoring")
    public ResponseEntity<HealthMonitoringService.MonitoringStats> getMonitoringStats() {
        return ResponseEntity.ok(healthMonitoringService.getMonitoringStats());
    }
}