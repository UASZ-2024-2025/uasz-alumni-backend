package com.uasz.alumni.alumnistudentcompany.utils;

import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;

import java.time.ZoneOffset;

/** Conversion entité JPA vers modèle de l'API généré depuis le contrat. */
public final class CompanyMapper {

    private CompanyMapper() {
    }

    public static Company toDto(CompanyEntity entity) {
        Company dto = new Company();
        dto.setId(entity.getId());
        dto.setOwnerId(entity.getOwnerId());
        dto.setName(entity.getName());
        dto.setSector(entity.getSector());
        dto.setDescription(entity.getDescription());
        dto.setWebsite(entity.getWebsite());
        dto.setAddress(entity.getAddress());
        dto.setContactEmail(entity.getContactEmail());
        dto.setContactPhone(entity.getContactPhone());
        dto.setLogoUrl(entity.getLogoUrl());
        dto.setIsPartner(entity.isPartner());
        dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
