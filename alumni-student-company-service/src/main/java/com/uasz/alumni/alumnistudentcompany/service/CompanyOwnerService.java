package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import com.uasz.alumni.alumnistudentcompany.repository.CompanyRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.utils.CompanyMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Rattachement d'une fiche à un compte ENTREPRISE par un administrateur ({@code PUT /companies/{id}/owner},
 * ALUM-168). Appel <b>synchrone</b> vers user-service : l'administrateur doit savoir si le rattachement a abouti.
 * user-service est appelé d'abord (409 si le compte n'est pas ENTREPRISE ou a déjà une autre fiche), puis le
 * gestionnaire de la fiche est enregistré. Pas de transaction ouverte pendant l'appel HTTP. Idempotent : rejouer
 * avec le même compte rattrape un rattachement manqué à la création.
 */
@Service
public class CompanyOwnerService {

    private static final Logger log = LoggerFactory.getLogger(CompanyOwnerService.class);

    private final CompanyRepository repository;
    private final UserServiceClient users;

    public CompanyOwnerService(CompanyRepository repository, UserServiceClient users) {
        this.repository = repository;
        this.users = users;
    }

    public Company assignOwner(UUID companyId, UUID ownerId, AuthenticatedUser caller) {
        if (!CompanyService.ADMIN_ROLE.equals(caller.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'administrateur peut rattacher une fiche à un compte");
        }
        if (ownerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Le champ ownerId est obligatoire");
        }
        CompanyEntity entity = repository.findById(companyId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Entreprise introuvable : " + companyId));

        try {
            users.linkCompany(ownerId, companyId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Compte introuvable : " + ownerId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_LINKABLE",
                    "Ce compte n'est pas un compte ENTREPRISE ou est déjà rattaché à une autre fiche");
        } catch (RuntimeException e) {
            log.error("Rattachement du compte {} à la fiche {} impossible : {}", ownerId, companyId, e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                    "user-service est indisponible : réessayez plus tard");
        }

        entity.setOwnerId(ownerId);
        return CompanyMapper.toDto(repository.save(entity));
    }
}
