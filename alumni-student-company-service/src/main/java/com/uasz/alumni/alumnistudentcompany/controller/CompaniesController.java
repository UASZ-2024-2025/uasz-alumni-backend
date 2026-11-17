package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.spi.api.CompaniesApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyUpdateRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompaniesController implements CompaniesApi {

    @Override
    public ResponseEntity<CompanyPagedResponse> searchCompanies(String query, Boolean isPartner, Integer page, Integer size) {
        // TODO : recherche paginée (nom/secteur, partenaire)
        throw new NotImplementedYetException("searchCompanies");
    }

    @Override
    public ResponseEntity<Company> createCompany(CompanyCreateRequest companyCreateRequest) {
        // TODO : créer la fiche entreprise -> 201 / 400
        throw new NotImplementedYetException("createCompany");
    }

    @Override
    public ResponseEntity<Company> getCompanyById(UUID id) {
        // TODO : détails -> 200 / 404
        throw new NotImplementedYetException("getCompanyById");
    }

    @Override
    public ResponseEntity<Company> updateCompany(UUID id, CompanyUpdateRequest companyUpdateRequest) {
        // TODO : mise à jour -> 200 / 400 / 404
        throw new NotImplementedYetException("updateCompany");
    }
}
