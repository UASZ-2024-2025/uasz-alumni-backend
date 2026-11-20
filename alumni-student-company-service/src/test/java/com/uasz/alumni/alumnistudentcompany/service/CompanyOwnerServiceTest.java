package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class CompanyOwnerServiceTest {

    private final CompanyRepository repository = mock(CompanyRepository.class);
    private final UserServiceClient users = mock(UserServiceClient.class);
    private final CompanyOwnerService service = new CompanyOwnerService(repository, users);

    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");
    private final AuthenticatedUser alumni = new AuthenticatedUser(UUID.randomUUID(), "a@uasz.sn", "ALUMNI");
    private final UUID companyId = UUID.randomUUID();
    private final UUID newOwner = UUID.randomUUID();
    private CompanyEntity company;

    @BeforeEach
    void setUp() {
        company = new CompanyEntity();
        company.setOwnerId(admin.id());
        company.setName("Gainde 2000");
        when(repository.findById(companyId)).thenReturn(Optional.of(company));
        when(repository.save(any(CompanyEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ApiException statusOf(Runnable action, HttpStatus expected) {
        ApiException ex = assertThrows(ApiException.class, action::run);
        assertEquals(expected, ex.getStatus());
        return ex;
    }

    @Test
    void lAdministrateurRattacheLaFicheAuCompte() {
        Company result = service.assignOwner(companyId, newOwner, admin);

        verify(users).linkCompany(newOwner, companyId);
        assertEquals(newOwner, company.getOwnerId());
        assertEquals(newOwner, result.getOwnerId());
    }

    @Test
    void seulLAdministrateurPeutRattacher() {
        statusOf(() -> service.assignOwner(companyId, newOwner, alumni), HttpStatus.FORBIDDEN);
        verify(users, never()).linkCompany(any(), any());
    }

    @Test
    void ficheInconnueDonne404EtOwnerIdAbsentDonne400() {
        statusOf(() -> service.assignOwner(UUID.randomUUID(), newOwner, admin), HttpStatus.NOT_FOUND);
        statusOf(() -> service.assignOwner(companyId, null, admin), HttpStatus.BAD_REQUEST);
    }

    @Test
    void unConflitDeUserServiceDonne409EtLaFicheNEstPasModifiee() {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null))
                .when(users).linkCompany(newOwner, companyId);

        statusOf(() -> service.assignOwner(companyId, newOwner, admin), HttpStatus.CONFLICT);

        assertEquals(admin.id(), company.getOwnerId());
        verify(repository, never()).save(any(CompanyEntity.class));
    }

    @Test
    void unCompteInconnuDonne404() {
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null))
                .when(users).linkCompany(newOwner, companyId);

        statusOf(() -> service.assignOwner(companyId, newOwner, admin), HttpStatus.NOT_FOUND);
    }

    @Test
    void userServiceIndisponibleDonne503() {
        doThrow(new ResourceAccessException("timeout")).when(users).linkCompany(newOwner, companyId);

        statusOf(() -> service.assignOwner(companyId, newOwner, admin), HttpStatus.SERVICE_UNAVAILABLE);
        verify(repository, never()).save(any(CompanyEntity.class));
    }
}
