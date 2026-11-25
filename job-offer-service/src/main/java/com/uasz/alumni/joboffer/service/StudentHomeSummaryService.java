package com.uasz.alumni.joboffer.service;

import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatusCounts;
import com.uasz.alumni.joboffer.spi.model.StudentHomeSummary;
import com.uasz.alumni.notificationmessaging.spi.model.UserUnreadCounts;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Synthèse de la page d'accueil d'un étudiant (ALUM-243) : ses candidatures par statut (calculées ici),
 * les propositions (0 tant que GET /proposals n'est pas implémenté) et les non-lus lus auprès de
 * notification-messaging-service. Si ce dernier est injoignable, les non-lus valent 0 : la page reste affichable.
 */
@Service
public class StudentHomeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(StudentHomeSummaryService.class);

    private final JobApplicationRepository applications;
    private final UnreadCountsClient unread;

    public StudentHomeSummaryService(JobApplicationRepository applications, UnreadCountsClient unread) {
        this.applications = applications;
        this.unread = unread;
    }

    // Pas de @Transactional : l'appel distant (jusqu'à plusieurs secondes) ne doit pas garder une connexion à la base.
    public StudentHomeSummary summary(UUID studentId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (JobApplicationRepository.StatusCount c : applications.countByStatusForStudent(studentId)) {
            counts.put(c.getStatus(), c.getTotal());
        }
        UserUnreadCounts unreadCounts = readUnread(studentId);
        return new StudentHomeSummary()
            .applicationsByStatus(new ApplicationStatusCounts()
                .submitted(count(counts, ApplicationStatus.SUBMITTED))
                .underReview(count(counts, ApplicationStatus.UNDER_REVIEW))
                .shortlisted(count(counts, ApplicationStatus.SHORTLISTED))
                .rejected(count(counts, ApplicationStatus.REJECTED))
                .accepted(count(counts, ApplicationStatus.ACCEPTED)))
            .proposalsReceived(0)
            .unreadNotifications(orZero(unreadCounts.getNotifications()))
            .unreadMessages(orZero(unreadCounts.getMessages()));
    }

    private UserUnreadCounts readUnread(UUID studentId) {
        try {
            UserUnreadCounts result = unread.unreadCounts(studentId);
            if (result != null) {
                return result;
            }
        } catch (RuntimeException e) {
            log.warn("Non-lus indisponibles pour la synthèse d'accueil : {}", e.toString());
        }
        return new UserUnreadCounts().notifications(0).messages(0);
    }

    private static int count(Map<ApplicationStatus, Long> counts, ApplicationStatus status) {
        return (int) Math.min(counts.getOrDefault(status, 0L), Integer.MAX_VALUE);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
