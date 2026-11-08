package com.uasz.alumni.joboffer.dto;

import com.uasz.alumni.joboffer.model.ApplicationEntity;
import com.uasz.alumni.joboffer.model.JobOfferEntity;
import com.uasz.alumni.joboffer.spi.model.Application;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

/** Conversion entités JPA vers modèles du contrat (SPI). Les entités ne sortent jamais du service. */
public final class JobOfferMapper {

    private JobOfferMapper() {
    }

    public static JobOffer toDto(JobOfferEntity e) {
        JobOffer dto = new JobOffer();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setType(e.getType());
        dto.setDescription(e.getDescription());
        dto.setCompanyId(e.getCompanyId());
        dto.setCompanyName(e.getCompanyName());
        dto.setPublisherId(e.getPublisherId());
        dto.setPublisherName(e.getPublisherName());
        dto.setLocation(e.getLocation());
        dto.setRemoteType(e.getRemoteType());
        dto.setRequiredSkills(new ArrayList<>(e.getRequiredSkills()));
        dto.setDurationMonths(e.getDurationMonths());
        dto.setSalaryRange(e.getSalaryRange());
        dto.setDeadline(e.getDeadline());
        dto.setStatus(e.getStatus());
        dto.setCreatedAt(utc(e.getCreatedAt()));
        return dto;
    }

    public static Application toDto(ApplicationEntity e) {
        Application dto = new Application();
        dto.setId(e.getId());
        dto.setOfferId(e.getOffer().getId());
        dto.setOfferTitle(e.getOffer().getTitle());
        dto.setStudentId(e.getStudentId());
        dto.setStudentName(e.getStudentName());
        dto.setCvId(e.getCvId());
        dto.setCoverLetter(e.getCoverLetter());
        dto.setStatus(e.getStatus());
        dto.setCreatedAt(utc(e.getCreatedAt()));
        return dto;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
