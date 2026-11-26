package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.analyticsmatching.service.DashboardService;
import com.uasz.alumni.analyticsmatching.spi.api.DashboardApi;
import com.uasz.alumni.analyticsmatching.spi.model.DashboardStats;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
