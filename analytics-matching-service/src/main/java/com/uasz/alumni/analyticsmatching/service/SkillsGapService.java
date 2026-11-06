package com.uasz.alumni.analyticsmatching.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.uasz.alumni.analyticsmatching.service.SkillsGapCalculator.SkillStat;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapItem;
import com.uasz.alumni.analyticsmatching.spi.model.SkillsGapResponse;

@Service
public class SkillsGapService {

    static final int TOP_REQUIRED_LIMIT = 5;

    private final SkillsDataSource dataSource;

    public SkillsGapService(SkillsDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SkillsGapResponse buildReport() {
        List<SkillStat> stats = SkillsGapCalculator.compute(
                dataSource.skillsRequiredByOffers(),
                dataSource.skillsOfStudentsAndAlumni());

        SkillsGapResponse response = new SkillsGapResponse();
        response.setTopRequiredSkills(SkillsGapCalculator.topRequired(stats, TOP_REQUIRED_LIMIT));
        response.setSkillsGapAnalysis(stats.stream().map(SkillsGapService::toItem).toList());
        return response;
    }

    private static SkillsGapItem toItem(SkillStat stat) {
        SkillsGapItem item = new SkillsGapItem();
        item.setSkillName(stat.skillName());
        item.setDemandPercentage(stat.demandPercentage());
        item.setSupplyPercentage(stat.supplyPercentage());
        item.setGap(stat.gap());
        return item;
    }
}
