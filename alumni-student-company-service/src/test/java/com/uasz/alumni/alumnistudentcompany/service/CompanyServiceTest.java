package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.CompanyUpdateRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@SuppressWarnings({"unchecked", "rawtypes"})
class CompanyServiceTest {

    private final CompanyRepository repository = mock(CompanyRepository.class);
    private final CompanyService service = new CompanyService(repository);

    private final AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), "owner@uasz.sn", "ALUMNI");
    private final AuthenticatedUser other = new AuthenticatedUser(UUID.randomUUID(), "other@uasz.sn", "ALUMNI");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");
    private final UUID companyId = UUID.randomUUID();
    private CompanyEntity company;

    @BeforeEach
    void setUp() {
        company = new CompanyEntity();
        company.setOwnerId(owner.id());
        company.setName("Gainde 2000");
        company.setSector("Informatique");
        company.setContactEmail("contact@gainde.sn");
        when(repository.findById(companyId)).thenReturn(Optional.of(company));
        when(repository.save(any(CompanyEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static CompanyCreateRequest createRequest(String name, String sector, String email) {
        return new CompanyCreateRequest().name(name).sector(sector).contactEmail(email);
    }

    private static ApiException statusOf(Runnable action, HttpStatus expected) {
        ApiException ex = assertThrows(ApiException.class, action::run);
        assertEquals(expected, ex.getStatus());
        return ex;
    }

    @Test
    void creerUneFicheFaitDuCreateurLeGestionnaireEtLaLaisseNonPartenaire() {
        Company created = service.create(createRequest("Gainde 2000", "Informatique", "contact@gainde.sn"), owner);

        ArgumentCaptor<CompanyEntity> saved = ArgumentCaptor.forClass(CompanyEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(owner.id(), saved.getValue().getOwnerId());
        assertFalse(saved.getValue().isPartner());
        assertEquals(owner.id(), created.getOwnerId());
        assertFalse(created.getIsPartner());
    }

    @Test
    void creerSansNomOuAvecNomVideDonne400() {
        statusOf(() -> service.create(createRequest("   ", "Informatique", "contact@gainde.sn"), owner),
                HttpStatus.BAD_REQUEST);
        statusOf(() -> service.create(createRequest("Gainde", "Informatique", null), owner), HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(CompanyEntity.class));
    }

    @Test
    void unNonProprietaireNePeutPasModifierLaFiche() {
        statusOf(() -> service.update(companyId, new CompanyUpdateRequest().description("modif"), other),
                HttpStatus.FORBIDDEN);
        verify(repository, never()).save(any(CompanyEntity.class));
    }

    @Test
    void leProprietaireModifieSaFiche() {
        Company updated = service.update(companyId, new CompanyUpdateRequest().description("Éditeur de logiciels"), owner);

        assertEquals("Éditeur de logiciels", updated.getDescription());
        assertEquals("Gainde 2000", updated.getName()); // champ non envoyé : inchangé
    }

    @Test
    void unAdministrateurPeutModifierLaFicheDUnAutre() {
        Company updated = service.update(companyId, new CompanyUpdateRequest().name("Gainde Group"), admin);

        assertEquals("Gainde Group", updated.getName());
        assertEquals(owner.id(), updated.getOwnerId()); // le gestionnaire ne change pas
    }

    @Test
    void leProprietaireNePeutPasSeDeclarerPartenaire() {
        statusOf(() -> service.update(companyId, new CompanyUpdateRequest().isPartner(true), owner), HttpStatus.FORBIDDEN);
        assertFalse(company.isPartner());
    }

    @Test
    void leProprietaireQuiRenvoieLeMemeStatutPartenaireNEstPasRefuse() {
        Company updated = service.update(companyId, new CompanyUpdateRequest().isPartner(false).description("ok"), owner);

        assertEquals("ok", updated.getDescription());
        assertFalse(updated.getIsPartner());
    }

    @Test
    void seulUnAdministrateurValideLePartenaire() {
        Company updated = service.update(companyId, new CompanyUpdateRequest().isPartner(true), admin);

        assertTrue(company.isPartner());
        assertTrue(updated.getIsPartner());
    }

    @Test
    void uneFicheInconnueDonne404() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        statusOf(() -> service.get(unknown), HttpStatus.NOT_FOUND);
        statusOf(() -> service.update(unknown, new CompanyUpdateRequest().description("x"), owner), HttpStatus.NOT_FOUND);
    }

    @Test
    void laPaginationEstBorneeEtParDefautVaut20() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(company)));

        CompanyPagedResponse bornee = service.search(null, null, -3, 500);
        CompanyPagedResponse parDefaut = service.search(null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getAllValues().get(0).getPageNumber());
        assertEquals(100, pageable.getAllValues().get(0).getPageSize());
        assertEquals(20, pageable.getAllValues().get(1).getPageSize());
        assertEquals(0, bornee.getPage());
        assertEquals(100, bornee.getSize());
        assertEquals(20, parDefaut.getSize());
        assertEquals(1, parDefaut.getTotalElements());
        assertEquals(1, parDefaut.getContent().size());
    }
}
