package com.uasz.alumni.user.service;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALUM-168 : rattachement d'un compte ENTREPRISE à sa fiche entreprise ({@code PUT /internal/users/{id}/company}).
 *
 * <p><b>Idempotent</b> : rattacher un compte à la fiche à laquelle il est déjà rattaché ne change rien. Un compte
 * ne gère qu'une fiche : un second rattachement à une autre fiche est refusé (409).
 */
@Service
public class CompanyLinkService {

    private final UserAccountRepository users;
    private final AuditService audit;

    public CompanyLinkService(UserAccountRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public UserAccount linkCompany(UUID userId, UUID companyId) {
        if (companyId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Le champ companyId est obligatoire.");
        }
        UserAccount account = users.findById(userId).orElseThrow(UserNotFoundException::new);

        if (account.getRole() != RoleName.ENTREPRISE) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_A_COMPANY_ACCOUNT",
                    "Seul un compte ENTREPRISE peut être rattaché à une fiche entreprise.");
        }
        if (companyId.equals(account.getCompanyId())) {
            return account;
        }
        if (account.getCompanyId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPANY_ALREADY_LINKED",
                    "Ce compte est déjà rattaché à une autre fiche entreprise.");
        }
        account.attachToCompany(companyId);
        users.save(account);
        // appel de service : pas d'utilisateur acteur
        audit.record("USER_COMPANY_LINKED", null, userId, "companyId=" + companyId);
        return account;
    }
}
