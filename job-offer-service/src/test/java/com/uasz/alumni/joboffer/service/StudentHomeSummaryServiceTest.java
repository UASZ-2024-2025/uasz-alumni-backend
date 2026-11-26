
package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.StudentHomeSummary;
import com.uasz.alumni.notificationmessaging.spi.model.UserUnreadCounts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentHomeSummaryServiceTest {

    @Mock
    private JobApplicationRepository applications;

    @Mock
    private UnreadCountsClient unread;

    @InjectMocks
    private StudentHomeSummaryService service;

    private UUID studentId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
    }

    @Test
    void calculeLesCandidaturesEtLesNonLus() {
        when(applications.countByStatusForStudent(studentId))
            .thenReturn(List.of(
                statusCount(ApplicationStatus.SUBMITTED, 3),
                statusCount(ApplicationStatus.UNDER_REVIEW, 2),
                statusCount(ApplicationStatus.SHORTLISTED, 1),
                statusCount(ApplicationStatus.REJECTED, 4),
                statusCount(ApplicationStatus.ACCEPTED, 5)
            ));

        when(unread.unreadCounts(studentId))
            .thenReturn(new UserUnreadCounts()
                .notifications(7)
                .messages(2));

        StudentHomeSummary result = service.summary(studentId);

        assertEquals(3, result.getApplicationsByStatus().getSubmitted());
        assertEquals(2, result.getApplicationsByStatus().getUnderReview());
        assertEquals(1, result.getApplicationsByStatus().getShortlisted());
        assertEquals(4, result.getApplicationsByStatus().getRejected());
        assertEquals(5, result.getApplicationsByStatus().getAccepted());

        assertEquals(0, result.getProposalsReceived());
        assertEquals(7, result.getUnreadNotifications());
        assertEquals(2, result.getUnreadMessages());

        verify(applications).countByStatusForStudent(studentId);
        verify(unread).unreadCounts(studentId);
    }

    @Test
    void metLesStatutsAbsentsAZero() {
        when(applications.countByStatusForStudent(studentId))
            .thenReturn(List.of(
                statusCount(ApplicationStatus.SUBMITTED, 2)
            ));

        when(unread.unreadCounts(studentId))
            .thenReturn(new UserUnreadCounts()
                .notifications(0)
                .messages(0));

        StudentHomeSummary result = service.summary(studentId);

        assertEquals(2, result.getApplicationsByStatus().getSubmitted());
        assertEquals(0, result.getApplicationsByStatus().getUnderReview());
        assertEquals(0, result.getApplicationsByStatus().getShortlisted());
        assertEquals(0, result.getApplicationsByStatus().getRejected());
        assertEquals(0, result.getApplicationsByStatus().getAccepted());
    }

    @Test
    void conserveLaSyntheseSiLeServiceDeNotificationsEstIndisponible() {
        when(applications.countByStatusForStudent(studentId))
            .thenReturn(List.of(
                statusCount(ApplicationStatus.SUBMITTED, 2)
            ));

        when(unread.unreadCounts(studentId))
            .thenThrow(new RuntimeException("Service indisponible"));

        StudentHomeSummary result = service.summary(studentId);

        assertEquals(2, result.getApplicationsByStatus().getSubmitted());
        assertEquals(0, result.getUnreadNotifications());
        assertEquals(0, result.getUnreadMessages());
    }

    private JobApplicationRepository.StatusCount statusCount(
        ApplicationStatus status, long total) {

        return new JobApplicationRepository.StatusCount() {
            @Override
            public ApplicationStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
