package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyUpdateRequest;
import com.uasz.alumni.alumnistudentcompany.utils.CompanyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Règles métier des fiches entreprises (ALUM-96). Le contrôle du rôle à la création est porté par @PreAuthorize sur
 * le contrôleur ; ici : gestionnaire de la fiche, statut de partenaire et validation des champs.
 */
@Service
public class CompanyService {

    static final String ADMIN_ROLE = "ADMINISTRATEUR";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanyPagedResponse search(String query, Boolean isPartner, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<CompanyEntity> spec = (root, q, cb) -> cb.conjunction();
        if (isPartner != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("partner"), isPartner));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("sector")), pattern)));
        }

        Page<CompanyEntity> result = repository.findAll(spec, PageRequest.of(pageNumber, pageSize, Sort.by("name")));

        CompanyPagedResponse response = new CompanyPagedResponse();
        response.setContent(result.getContent().stream().map(CompanyMapper::toDto).toList());
        response.setTotalElements(Math.toIntExact(result.getTotalElements()));
        response.setPage(pageNumber);
        response.setSize(pageSize);
        return response;
    }

    @Transactional(readOnly = true)
    public Company get(UUID id) {
        return CompanyMapper.toDto(findOrThrow(id));
    }

    @Transactional
    public Company create(CompanyCreateRequest request, AuthenticatedUser caller) {
        CompanyEntity entity = new CompanyEntity();
        entity.setOwnerId(caller.id());
        entity.setName(requireText(request.getName(), "name"));
        entity.setSector(requireText(request.getSector(), "sector"));
        entity.setContactEmail(requireText(request.getContactEmail(), "contactEmail"));
        entity.setDescription(request.getDescription());
        entity.setWebsite(request.getWebsite());
        entity.setAddress(request.getAddress());
        entity.setContactPhone(request.getContactPhone());
        entity.setLogoUrl(request.getLogoUrl());
        entity.setPartner(false); // jamais partenaire à la création : seul l'administrateur valide

        return CompanyMapper.toDto(repository.save(entity));
    }

    @Transactional
    public Company update(UUID id, CompanyUpdateRequest request, AuthenticatedUser caller) {
        CompanyEntity entity = findOrThrow(id);
        boolean admin = ADMIN_ROLE.equals(caller.role());

        if (!admin && !entity.getOwnerId().equals(caller.id())) {
            throw forbidden("Seul le gestionnaire de la fiche ou l'administrateur peut la modifier");
        }
        if (request.getIsPartner() != null && request.getIsPartner() != entity.isPartner()) {
            if (!admin) {
                throw forbidden("Seul l'administrateur peut modifier le statut de partenaire");
            }
            entity.setPartner(request.getIsPartner());
        }

        if (request.getName() != null) {
            entity.setName(requireText(request.getName(), "name"));
        }
        if (request.getSector() != null) {
            entity.setSector(requireText(request.getSector(), "sector"));
        }
        if (request.getContactEmail() != null) {
            entity.setContactEmail(requireText(request.getContactEmail(), "contactEmail"));
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getWebsite() != null) {
            entity.setWebsite(request.getWebsite());
        }
        if (request.getAddress() != null) {
            entity.setAddress(request.getAddress());
        }
        if (request.getContactPhone() != null) {
            entity.setContactPhone(request.getContactPhone());
        }
        if (request.getLogoUrl() != null) {
            entity.setLogoUrl(request.getLogoUrl());
        }

        return CompanyMapper.toDto(repository.save(entity));
    }

    private CompanyEntity findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Entreprise introuvable : " + id));
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Le champ " + field + " est obligatoire");
        }
        return value.trim();
    }
}
