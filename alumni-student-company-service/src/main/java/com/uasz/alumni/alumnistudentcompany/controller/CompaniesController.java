package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.service.CompanyService;
import com.uasz.alumni.alumnistudentcompany.spi.api.CompaniesApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implémentation de l'interface générée {@link CompaniesApi} : les routes viennent du contrat. */
@RestController
public class CompaniesController implements CompaniesApi {

    private final CompanyService service;

    public CompaniesController(CompanyService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<CompanyPagedResponse> searchCompanies(String query, Boolean isPartner, Integer page, Integer size) {
        return ResponseEntity.ok(service.search(query, isPartner, page, size));
    }

    @Override
    public ResponseEntity<Company> createCompany(CompanyCreateRequest companyCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(companyCreateRequest));
    }

    @Override
    public ResponseEntity<Company> getCompanyById(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<Company> updateCompany(UUID id, CompanyUpdateRequest companyUpdateRequest) {
        return ResponseEntity.ok(service.update(id, companyUpdateRequest));
    }
}
