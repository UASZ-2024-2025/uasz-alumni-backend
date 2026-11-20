package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.apiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CompanyLinkServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CompanyLinkService service = new CompanyLinkService(users, audit);
    private final UUID companyId = UUID.randomUUID();

    private UserAccount existing(RoleName role) {
        UserAccount account = account(UUID.randomUUID(), "rh@gainde.sn", role, UserStatus.ACTIF);
        when(users.findById(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void rattacheUnCompteEntrepriseEtAuditeSansActeur() {
        UserAccount account = existing(RoleName.ENTREPRISE);

        UserAccount linked = service.linkCompany(account.getId(), companyId);

        assertThat(linked.getCompanyId()).isEqualTo(companyId);
        verify(users).save(account);
        verify(audit).record(eq("USER_COMPANY_LINKED"), eq(null), eq(account.getId()), any());
    }

    @Test
    void rejouerLeMemeRattachementEstIdempotent() {
        UserAccount account = existing(RoleName.ENTREPRISE);
        account.attachToCompany(companyId);

        assertThat(service.linkCompany(account.getId(), companyId).getCompanyId()).isEqualTo(companyId);

        verify(users, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void uneAutreFicheDonne409() {
        UserAccount account = existing(RoleName.ENTREPRISE);
        account.attachToCompany(UUID.randomUUID());

        assertThatThrownBy(() -> service.linkCompany(account.getId(), companyId))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.CONFLICT, "COMPANY_ALREADY_LINKED"));
        verify(users, never()).save(any());
    }

    @Test
    void seulUnCompteEntreprisePeutEtreRattache() {
        UserAccount alumni = existing(RoleName.ALUMNI);

        assertThatThrownBy(() -> service.linkCompany(alumni.getId(), companyId))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.CONFLICT, "NOT_A_COMPANY_ACCOUNT"));
    }

    @Test
    void compteInconnuDonne404() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkCompany(unknown, companyId))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }

    @Test
    void companyIdAbsentDonne400() {
        assertThatThrownBy(() -> service.linkCompany(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"));
    }
}
