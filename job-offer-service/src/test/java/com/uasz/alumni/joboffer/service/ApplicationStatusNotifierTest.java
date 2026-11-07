package com.uasz.alumni.joboffer.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationStatusNotifierTest {

    private final NotificationClient client = mock(NotificationClient.class);
    private final ApplicationStatusNotifier notifier = new ApplicationStatusNotifier(client);

    @Test
    void envoieUneNotificationApplicationStatusChangedALEtudiant() {
        UUID applicationId = UUID.randomUUID();
        UUID student = UUID.randomUUID();

        notifier.onStatusChanged(new ApplicationStatusChanged(applicationId, student, "Développeur Java",
                ApplicationStatus.SHORTLISTED, "jeton"));

        verify(client).send(eq(student), eq("APPLICATION_STATUS_CHANGED"), eq("Mise à jour de votre candidature"),
                contains("présélectionnée"), eq(applicationId), eq("jeton"));
    }
}
