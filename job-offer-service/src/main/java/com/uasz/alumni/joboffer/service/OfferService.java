package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.JobOfferCreateRequest;
import com.uasz.alumni.joboffer.spi.model.JobOfferUpdateRequest;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import com.uasz.alumni.joboffer.spi.model.RemoteType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALUM-148 : publication et gestion des offres d'emploi et de stage.
 *
 * <ul>
 *   <li><b>Publier</b> : ENTREPRISE, ALUMNI ou ADMINISTRATEUR ; l'appelant gère la fiche (sauf administrateur) ; l'entreprise
 *       est <b>validée</b> ({@code isPartner}), sinon 403. La fiche est lue dans alumni-student-company-service
 *       (jeton de service, ADR-0010) ; son gestionnaire ({@code ownerId}) fait foi.</li>
 *   <li><b>Modifier, changer le statut, supprimer</b> : l'auteur de l'offre ou un ADMINISTRATEUR, sinon 403.</li>
 *   <li><b>Consulter</b> : publique pour les offres OPEN ; les autres statuts sont réservés à l'auteur et à
 *       l'administrateur.</li>
 *   <li><b>Date limite</b> : une offre OPEN passe à CLOSED le lendemain de sa date limite ({@link #closeExpiredOffers()}).</li>
 * </ul>
 */
@Service
public class OfferService {

    static final String ADMIN_ROLE = "ADMINISTRATEUR";
    static final String ENTERPRISE_ROLE = "ENTREPRISE";
    static final Set<String> PUBLISHER_ROLES = Set.of(ENTERPRISE_ROLE, "ALUMNI", ADMIN_ROLE);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TITLE_MAX = 255;
    private static final int LOCATION_MAX = 255;
    private static final int DESCRIPTION_MAX = 10_000;
    private static final int SALARY_MAX = 100;
    private static final int SKILL_MAX = 100;
    private static final int SKILLS_MAX_COUNT = 30;
    private static final int DURATION_MAX_MONTHS = 120;

    private final OfferRepository offers;
    private final JobApplicationRepository applications;
    private final CompanyClient companies;
    private final Clock clock;

    public OfferService(OfferRepository offers, JobApplicationRepository applications, CompanyClient companies,
                        Clock clock) {
        this.offers = offers;
        this.applications = applications;
        this.companies = companies;
        this.clock = clock;
    }

    /** Critères de recherche (paramètres de {@code searchOffers}). */
    public record Search(String query, OfferType type, UUID companyId, String location, RemoteType remoteType,
                         OfferStatus status) {
    }

    // ───── consultation ─────

    @Transactional(readOnly = true)
    public Page<Offer> search(Search criteria, AuthenticatedUser caller, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LocalDate today = LocalDate.now(clock);
        OfferStatus status = criteria.status() == null ? OfferStatus.OPEN : criteria.status();

        Specification<Offer> spec = (root, q, cb) -> cb.equal(root.get("status"), status);
        if (status == OfferStatus.OPEN) {
            // une offre dont la date limite est dépassée n'est plus proposée, même avant le passage de la tâche planifiée
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.isNull(root.get("deadline")),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("deadline"), today)));
        } else if (!isAdmin(caller)) {
            // CLOSED et ARCHIVED : visibles de leur seul auteur
            if (caller == null) {
                spec = spec.and((root, q, cb) -> cb.disjunction());
            } else {
                UUID me = caller.id();
                spec = spec.and((root, q, cb) -> cb.equal(root.get("publisherId"), me));
            }
        }
        if (criteria.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), criteria.type()));
        }
        if (criteria.companyId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("companyId"), criteria.companyId()));
        }
        if (criteria.remoteType() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("remoteType"), criteria.remoteType()));
        }
        if (criteria.location() != null && !criteria.location().isBlank()) {
            String pattern = "%" + criteria.location().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("location")), pattern));
        }
        if (criteria.query() != null && !criteria.query().isBlank()) {
            String pattern = "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%";
            // intitulé, entreprise ou compétence requise
            spec = spec.and((root, q, cb) -> {
                Subquery<Integer> matchingSkill = q.subquery(Integer.class);
                Root<Offer> sub = matchingSkill.from(Offer.class);
                Join<Offer, String> skill = sub.join("requiredSkills");
                matchingSkill.select(cb.literal(1)).where(
                        cb.equal(sub.get("id"), root.get("id")),
                        cb.like(cb.lower(skill), pattern));
                return cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("companyName")), pattern),
                        cb.exists(matchingSkill));
            });
        }

        return offers.findAll(spec, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Offer get(UUID id, AuthenticatedUser caller) {
        Offer offer = find(id);
        if (offer.getStatus() != OfferStatus.OPEN && !canManage(offer, caller)) {
            throw notFound(id); // ne révèle pas l'existence d'une offre non publique
        }
        return offer;
    }

    // ───── publication ─────

    /**
     * Pas de {@code @Transactional} : la lecture de la fiche (appel HTTP) ne doit pas tenir une transaction ouverte ;
     * l'enregistrement final est atomique.
     */
    public Offer create(JobOfferCreateRequest request, AuthenticatedUser caller) {
        if (caller == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise.");
        }
        if (!PUBLISHER_ROLES.contains(caller.role())) {
            throw forbidden("FORBIDDEN", "Seuls les comptes ENTREPRISE, ALUMNI et ADMINISTRATEUR peuvent publier une offre");
        }
        LocalDate today = LocalDate.now(clock);

        String title = requireText(request.getTitle(), "title", TITLE_MAX);
        OfferType type = require(request.getType(), "type");
        String description = requireText(request.getDescription(), "description", DESCRIPTION_MAX);
        UUID companyId = require(request.getCompanyId(), "companyId");
        String location = requireText(request.getLocation(), "location", LOCATION_MAX);
        Set<String> skills = normalizeSkills(request.getRequiredSkills());
        if (skills.isEmpty()) {
            throw bad("VALIDATION_ERROR", "Le champ requiredSkills doit contenir au moins une compétence");
        }
        Integer duration = checkDuration(request.getDurationMonths());
        String salary = blankToNull(optionalText(request.getSalaryRange(), "salaryRange", SALARY_MAX));
        LocalDate deadline = checkDeadline(request.getDeadline(), today);

        Company company = authorizePublication(companyId, caller);

        Offer offer = new Offer(title, type, description, companyId, caller.id(), location);
        offer.completeDetails(company.getName(), caller.name(), request.getRemoteType(), skills, duration, salary,
                deadline);
        return offers.save(offer);
    }

    /**
     * L'entreprise est retrouvée grâce à {@code companyId} : celui du jeton pour un compte ENTREPRISE rattaché
     * (ALUM-168), sinon celui de la requête. Le gestionnaire de la fiche ({@code ownerId}) fait foi.
     */
    private Company authorizePublication(UUID companyId, AuthenticatedUser caller) {
        boolean admin = isAdmin(caller);
        boolean enterpriseAccount = ENTERPRISE_ROLE.equals(caller.role());

        if (!admin && enterpriseAccount && caller.companyId() != null && !caller.companyId().equals(companyId)) {
            throw forbidden("COMPANY_MISMATCH", "Vous ne pouvez publier qu'au nom de votre entreprise");
        }
        Company company = lookupCompany(companyId);
        if (!admin && !caller.id().equals(company.getOwnerId())) {
            if (enterpriseAccount && caller.companyId() == null) {
                throw forbidden("ACCOUNT_NOT_LINKED", "Votre compte n'est rattaché à aucune fiche entreprise");
            }
            throw forbidden("NOT_COMPANY_MANAGER", "Vous n'êtes pas le gestionnaire de cette entreprise");
        }
        if (!Boolean.TRUE.equals(company.getIsPartner())) {
            throw forbidden("COMPANY_NOT_VALIDATED",
                    "L'entreprise n'est pas encore validée : la publication d'offres est refusée");
        }
        return company;
    }

    private Company lookupCompany(UUID companyId) {
        Optional<Company> company;
        try {
            company = companies.find(companyId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COMPANY_SERVICE_UNAVAILABLE",
                    "La fiche entreprise est momentanément indisponible : réessayez plus tard");
        }
        return company.orElseThrow(() -> bad("COMPANY_NOT_FOUND", "Entreprise introuvable : " + companyId));
    }

    // ───── gestion ─────

    @Transactional
    public Offer update(UUID id, JobOfferUpdateRequest request, AuthenticatedUser caller) {
        Offer offer = find(id);
        requireManager(offer, caller);
        if (offer.getStatus() == OfferStatus.ARCHIVED) {
            throw bad("OFFER_ARCHIVED", "Une offre archivée n'est plus modifiable");
        }
        LocalDate today = LocalDate.now(clock);

        String title = request.getTitle() == null ? null : requireText(request.getTitle(), "title", TITLE_MAX);
        String description = request.getDescription() == null ? null
                : requireText(request.getDescription(), "description", DESCRIPTION_MAX);
        String location = request.getLocation() == null ? null
                : requireText(request.getLocation(), "location", LOCATION_MAX);
        Set<String> skills = request.getRequiredSkills() == null ? null : normalizeSkills(request.getRequiredSkills());
        Integer duration = checkDuration(request.getDurationMonths());
        String salary = optionalText(request.getSalaryRange(), "salaryRange", SALARY_MAX);
        LocalDate deadline = checkDeadline(request.getDeadline(), today);

        offer.update(title, request.getType(), description, location, request.getRemoteType(), skills, duration,
                salary, deadline);
        return offer;
    }

    @Transactional
    public Offer changeStatus(UUID id, OfferStatus newStatus, AuthenticatedUser caller) {
        if (newStatus == null) {
            throw bad("VALIDATION_ERROR", "Le champ status est obligatoire");
        }
        Offer offer = find(id);
        requireManager(offer, caller);
        OfferStatus current = offer.getStatus();
        if (current == newStatus) {
            return offer;
        }
        if (!isTransitionAllowed(current, newStatus)) {
            throw bad("INVALID_STATUS_TRANSITION", "Transition de statut interdite : " + current + " -> " + newStatus);
        }
        if (newStatus == OfferStatus.OPEN && offer.getDeadline() != null
                && offer.getDeadline().isBefore(LocalDate.now(clock))) {
            throw bad("DEADLINE_PASSED", "La date limite est dépassée : modifiez-la avant de rouvrir l'offre");
        }
        offer.changeStatus(newStatus);
        return offer;
    }

    /** Suppression si l'offre n'a reçu aucune candidature ; sinon archivage (l'historique est conservé). */
    @Transactional
    public void delete(UUID id, AuthenticatedUser caller) {
        Offer offer = find(id);
        requireManager(offer, caller);
        if (applications.existsByOfferId(id)) {
            offer.changeStatus(OfferStatus.ARCHIVED);
        } else {
            offers.delete(offer);
        }
    }

    /** Ferme les offres OPEN dont la date limite est passée (appelée chaque nuit par {@link OfferExpirationJob}). */
    @Transactional
    public int closeExpiredOffers() {
        return offers.closeExpired(LocalDate.now(clock));
    }

    // ───── règles et validations ─────

    static boolean isTransitionAllowed(OfferStatus from, OfferStatus to) {
        return switch (from) {
            case OPEN -> to == OfferStatus.CLOSED || to == OfferStatus.ARCHIVED;
            case CLOSED -> to == OfferStatus.OPEN || to == OfferStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private Offer find(UUID id) {
        return offers.findById(id).orElseThrow(() -> notFound(id));
    }

    private static boolean isAdmin(AuthenticatedUser caller) {
        return caller != null && ADMIN_ROLE.equals(caller.role());
    }

    private static boolean canManage(Offer offer, AuthenticatedUser caller) {
        return caller != null && (isAdmin(caller) || offer.isPublishedBy(caller.id()));
    }

    private static void requireManager(Offer offer, AuthenticatedUser caller) {
        if (caller == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise.");
        }
        if (!canManage(offer, caller)) {
            throw forbidden("FORBIDDEN", "Seul l'auteur de l'offre ou un administrateur peut la modifier");
        }
    }

    private static ApiException notFound(UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + id);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw bad("VALIDATION_ERROR", "Le champ " + field + " est obligatoire");
        }
        return value;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw bad("VALIDATION_ERROR", "Le champ " + field + " est obligatoire");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw bad("VALIDATION_ERROR", "Le champ " + field + " ne doit pas dépasser " + max + " caractères");
        }
        return trimmed;
    }

    /** Null reste null (champ absent) ; une valeur vide reste vide (permet d'effacer à la mise à jour). */
    private static String optionalText(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw bad("VALIDATION_ERROR", "Le champ " + field + " ne doit pas dépasser " + max + " caractères");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static Integer checkDuration(Integer months) {
        if (months != null && (months < 1 || months > DURATION_MAX_MONTHS)) {
            throw bad("VALIDATION_ERROR", "Le champ durationMonths doit être compris entre 1 et " + DURATION_MAX_MONTHS);
        }
        return months;
    }

    private static LocalDate checkDeadline(LocalDate deadline, LocalDate today) {
        if (deadline != null && deadline.isBefore(today)) {
            throw bad("VALIDATION_ERROR", "La date limite ne peut pas être dans le passé");
        }
        return deadline;
    }

    /** Compétences nettoyées : espaces retirés, vides ignorées, doublons (sans tenir compte de la casse) supprimés. */
    private static Set<String> normalizeSkills(List<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                String skill = value.trim();
                if (skill.length() > SKILL_MAX) {
                    throw bad("VALIDATION_ERROR", "Une compétence ne doit pas dépasser " + SKILL_MAX + " caractères");
                }
                if (seen.add(skill.toLowerCase(Locale.ROOT))) {
                    result.add(skill);
                }
            }
        }
        if (result.size() > SKILLS_MAX_COUNT) {
            throw bad("VALIDATION_ERROR", "Une offre ne peut pas compter plus de " + SKILLS_MAX_COUNT + " compétences");
        }
        return result;
    }
}
