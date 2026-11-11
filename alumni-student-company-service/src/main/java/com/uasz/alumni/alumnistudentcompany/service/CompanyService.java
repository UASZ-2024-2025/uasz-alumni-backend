package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUser;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Règles métier des fiches entreprises (AL-28). */
@Service
public class CompanyService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public CompanyService(CompanyRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
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
    public Company create(CompanyCreateRequest request) {
        CurrentUser user = currentUserProvider.require();
        if (!user.admin() && !hasAnyRole("ALUMNI", "ENTREPRISE")) {
            throw forbidden("Seul un Alumni, une entreprise ou l'administrateur peut créer une fiche entreprise");
        }

        CompanyEntity entity = new CompanyEntity();
        entity.setOwnerId(user.userId());
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
    public Company update(UUID id, CompanyUpdateRequest request) {
        CompanyEntity entity = findOrThrow(id);
        CurrentUser user = currentUserProvider.require();

        if (!user.admin() && !entity.getOwnerId().equals(user.userId())) {
            throw forbidden("Seul le gestionnaire de la fiche ou l'administrateur peut la modifier");
        }
        if (request.getIsPartner() != null && request.getIsPartner() != entity.isPartner()) {
            if (!user.admin()) {
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

    /** Le record CurrentUser ne porte que le statut admin : les autres rôles sont lus dans les autorités du JWT. */
    private static boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (String role : roles) {
            String authority = "ROLE_" + role;
            if (auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()))) {
                return true;
            }
        }
        return false;
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
