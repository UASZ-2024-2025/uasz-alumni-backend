package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Création du profil d'un compte qui vient d'être activé dans user-service (ALUM-83, {@code POST /internal/profiles}).
 *
 * <p><b>Idempotent</b> sur {@code userId} : user-service rejoue la requête après un timeout, il ne doit pas en
 * résulter de doublon. Un profil existant est renvoyé tel quel ({@code created = false}).
 *
 * <ul>
 *   <li>ALUMNI : profil créé dans {@code alumni_profiles} (nom, prénom, email du compte).</li>
 *   <li>ETUDIANT : la table {@code students} arrive avec ALUM-92 (profil étudiant) ; en attendant, 501.</li>
 * </ul>
 */
@Service
public class ProfileProvisioningService {

    private final AlumniRepository alumni;
    private final TransactionTemplate tx;

    public ProfileProvisioningService(AlumniRepository alumni, TransactionTemplate tx) {
        this.alumni = alumni;
        this.tx = tx;
    }

    /** Résultat : le profil, et s'il vient d'être créé (201) ou existait déjà (200). */
    public record Result(ProfileProvisioningResponse profile, boolean created) {
    }

    public Result provision(ProfileProvisioningRequest request) {
        return switch (request.getProfileType()) {
            case ALUMNI -> provisionAlumni(request);
            case ETUDIANT -> throw new NotImplementedYetException("provisionProfile (ETUDIANT, après ALUM-92)");
        };
    }

    private Result provisionAlumni(ProfileProvisioningRequest request) {
        var existing = alumni.findByUserId(request.getUserId());
        if (existing.isPresent()) {
            return new Result(toResponse(existing.get()), false);
        }
        try {
            // transaction courte et séparée : en cas de course, la violation d'unicité est levée ici
            Alumni saved = tx.execute(status -> alumni.saveAndFlush(Alumni.provisioned(
                    request.getUserId(), request.getFirstName(), request.getLastName(), request.getEmail())));
            return new Result(toResponse(saved), true);
        } catch (DataIntegrityViolationException e) {
            // deux appels simultanés pour le même compte (retry de user-service) : l'autre a créé le profil
            Alumni winner = alumni.findByUserId(request.getUserId()).orElseThrow(() -> e);
            return new Result(toResponse(winner), false);
        }
    }

    private static ProfileProvisioningResponse toResponse(Alumni a) {
        return new ProfileProvisioningResponse(a.getId(), a.getUserId(), ProvisionedProfileType.ALUMNI);
    }
}
