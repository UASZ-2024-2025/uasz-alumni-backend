package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.model.ProposalEntity;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.repository.ProposalRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.ProposalCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ProposalStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * ALUM-174 : propositions directes d'offres aux étudiants.
 *
 * <ul>
 *   <li>Un recruteur (auteur de l'offre, ou administrateur) propose une offre ouverte à un étudiant.</li>
 *   <li>L'étudiant destinataire est notifié ({@code NEW_JOB_PROPOSAL}) et peut accepter ou refuser.</li>
 *   <li>Les propositions restées {@code PENDING} au-delà de leur échéance expirent automatiquement ({@code EXPIRED}).</li>
 * </ul>
 */
@Service
public class ProposalService {

    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final String STUDENT = "ETUDIANT";
    private static final String ADMIN = "ADMINISTRATEUR";

    private final OfferRepository offers;
    private final ProposalRepository proposals;
    private final ApplicationEventPublisher events;
    private final StudentDirectory students;
    private final int expirationDays;

    public ProposalService(OfferRepository offers,
                           ProposalRepository proposals,
                           ApplicationEventPublisher events,
                           StudentDirectory students,
                           @Value("${proposals.expiration-days:14}") int expirationDays) {
        this.offers = offers;
        this.proposals = proposals;
        this.events = events;
        this.students = students;
        this.expirationDays = Math.max(1, expirationDays);
    }

    @Transactional(readOnly = true)
    public List<ProposalEntity> list(UUID studentId, UUID recruiterId, ProposalStatus status, AuthenticatedUser caller) {
        UUID effectiveStudentId = studentId;
        UUID effectiveRecruiterId = recruiterId;
        if (!ADMIN.equals(caller.role())) {
            if (STUDENT.equals(caller.role())) {
                if (studentId != null && !studentId.equals(caller.id())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                            "Un étudiant ne peut consulter que ses propres propositions");
                }
                effectiveStudentId = caller.id();
            } else {
                if (recruiterId != null && !recruiterId.equals(caller.id())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                            "Un recruteur ne peut consulter que ses propres propositions");
                }
                effectiveRecruiterId = caller.id();
            }
        }
        return proposals.search(effectiveStudentId, effectiveRecruiterId, status);
    }

    @Transactional
    public ProposalEntity create(ProposalCreateRequest request, AuthenticatedUser caller) {
        if (STUDENT.equals(caller.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Un étudiant ne peut pas envoyer de proposition d'offre");
        }
        Offer offer = offers.findById(request.getOfferId()).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offre introuvable : " + request.getOfferId()));
        if (!ADMIN.equals(caller.role()) && !offer.isPublishedBy(caller.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'auteur de l'offre ou un administrateur peut proposer cette offre");
        }
        LocalDate deadline = offer.getDeadline();
        if (offer.getStatus() != OfferStatus.OPEN || (deadline != null && deadline.isBefore(LocalDate.now()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER_NOT_OPEN",
                    "Cette offre n'est plus ouverte aux propositions");
        }
        String message = request.getMessage() == null ? null : request.getMessage().trim();
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "message : " + MAX_MESSAGE_LENGTH + " caractères maximum");
        }
        // L'étudiant est résolu auprès de son service : il existe, et on garde son id utilisateur (celui des
        // notifications et de GET /proposals), même si le recruteur a fourni l'id du profil.
        StudentDirectory.StudentRef student = resolveStudent(request.getStudentId());
        if (student.userId().equals(caller.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECIPIENT",
                    "Impossible d'envoyer une proposition à soi-même");
        }
        if (proposals.existsByOfferIdAndStudentIdAndStatus(offer.getId(), student.userId(), ProposalStatus.PENDING)) {
            throw duplicateProposal();
        }

        OffsetDateTime expiresAt = computeExpiration(deadline);
        String recruiterName = displayName(caller);
        ProposalEntity entity = new ProposalEntity(
                offer,
                student.userId(),
                student.fullName(),
                caller.id(),
                recruiterName,
                message == null || message.isEmpty() ? null : message,
                expiresAt);
        try {
            proposals.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw duplicateProposal();
        }

        events.publishEvent(new ProposalCreatedEvent(
                entity.getId(), entity.getStudentId(), offer.getTitle(), recruiterName));
        return entity;
    }

    /** Une proposition échue passe à EXPIRED même si la réponse est refusée : pas de rollback sur ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public ProposalEntity updateStatus(UUID id, ProposalStatus newStatus, AuthenticatedUser caller) {
        ProposalEntity proposal = proposals.findWithOfferById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Proposition introuvable : " + id));
        if (!proposal.getStudentId().equals(caller.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'étudiant destinataire peut répondre à cette proposition");
        }
        if (proposal.isExpiredAt(OffsetDateTime.now())) {
            proposal.markExpired();
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROPOSAL_EXPIRED",
                    "Cette proposition a expiré");
        }
        if (!ProposalEntity.isStudentResponseAllowed(proposal.getStatus(), newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Transition de statut interdite : " + proposal.getStatus() + " -> " + newStatus);
        }

        proposal.respond(newStatus);
        events.publishEvent(new ProposalRespondedEvent(
                proposal.getId(),
                proposal.getRecruiterId(),
                displayName(caller),
                proposal.getOffer().getTitle(),
                newStatus));
        return proposal;
    }

    @Transactional
    public int expirePendingProposals() {
        return proposals.expirePendingProposals(
                ProposalStatus.PENDING, ProposalStatus.EXPIRED, OffsetDateTime.now());
    }

    private OffsetDateTime computeExpiration(LocalDate offerDeadline) {
        OffsetDateTime defaultExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(expirationDays);
        if (offerDeadline == null) {
            return defaultExpiry;
        }
        OffsetDateTime deadlineEnd = offerDeadline.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return deadlineEnd.isBefore(defaultExpiry) ? deadlineEnd : defaultExpiry;
    }

    private StudentDirectory.StudentRef resolveStudent(UUID studentId) {
        try {
            return students.find(studentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    "STUDENT_NOT_FOUND", "Étudiant introuvable : " + studentId));
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STUDENT_SERVICE_UNAVAILABLE",
                    "Profil étudiant momentanément indisponible, réessayez plus tard");
        }
    }

    private static ApiException duplicateProposal() {
        return new ApiException(HttpStatus.CONFLICT, "PROPOSAL_ALREADY_PENDING",
                "Une proposition en attente existe déjà pour cet étudiant sur cette offre");
    }

    private static String displayName(AuthenticatedUser user) {
        return (user.name() != null && !user.name().isBlank()) ? user.name() : user.email();
    }
}
