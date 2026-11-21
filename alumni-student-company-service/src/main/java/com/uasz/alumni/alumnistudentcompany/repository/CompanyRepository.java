package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** Accès aux fiches entreprises. La recherche filtrée passe par les Specifications du service. */
public interface CompanyRepository extends JpaRepository<CompanyEntity, UUID>, JpaSpecificationExecutor<CompanyEntity> {

    /** Vrai si cet utilisateur gère déjà au moins une fiche (un compte ENTREPRISE n'en gère qu'une). */
    boolean existsByOwnerId(UUID ownerId);

    /** Fiche gérée par ce compte (la plus ancienne si un ALUMNI ou un administrateur en gère plusieurs). */
    Optional<CompanyEntity> findFirstByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
