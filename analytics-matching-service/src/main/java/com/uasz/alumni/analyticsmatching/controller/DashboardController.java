package com.uasz.alumni.analyticsmatching.controller;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;
import com.uasz.alumni.analyticsmatching.spi.api.DashboardApi;
import com.uasz.alumni.analyticsmatching.spi.model.DashboardStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController implements DashboardApi {

    @Override
    public ResponseEntity<DashboardStats> getDashboardStats() {
        // TODO : KPI globaux (ADMINISTRATEUR) -> 200 / 403
        throw new NotImplementedYetException("getDashboardStats");
    }
}
