package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileProvisioningServiceTest {

    private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Mock AlumniRepository alumni;
    @Mock StudentProfileRepository students;
    @Mock TransactionTemplate tx;

    private ProfileProvisioningService service;

    @BeforeEach
    void setUp() {
        when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(alumni.saveAndFlush(any(Alumni.class))).thenAnswer(inv -> inv.getArgument(0));
        when(students.saveAndFlush(any(StudentProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ProfileProvisioningService(alumni, students, tx);
    }

    private static ProfileProvisioningRequest request(ProvisionedProfileType type) {
        return new ProfileProvisioningRequest(USER, type, "Awa", "Diop", "awa.diop@uasz.sn");
    }

    @Test
    void createsTheAlumniProfileWithTheAccountIdentity() {
        when(alumni.findByUserId(USER)).thenReturn(Optional.empty());

        ProfileProvisioningService.Result result = service.provision(request(ProvisionedProfileType.ALUMNI));

        ArgumentCaptor<Alumni> saved = ArgumentCaptor.forClass(Alumni.class);
        verify(alumni).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getFirstName()).isEqualTo("Awa");
        assertThat(saved.getValue().getLastName()).isEqualTo("Diop");
        assertThat(saved.getValue().getEmail()).isEqualTo("awa.diop@uasz.sn");

        assertThat(result.created()).isTrue();
        assertThat(result.profile().getProfileId()).isEqualTo(saved.getValue().getId());
        assertThat(result.profile().getUserId()).isEqualTo(USER);
        assertThat(result.profile().getProfileType()).isEqualTo(ProvisionedProfileType.ALUMNI);
    }

    @Test
    void anExistingProfileIsReturnedWithoutDuplicate() {
        Alumni existing = Alumni.provisioned(USER, "Awa", "Diop", "awa.diop@uasz.sn");
        when(alumni.findByUserId(USER)).thenReturn(Optional.of(existing));

        ProfileProvisioningService.Result result = service.provision(request(ProvisionedProfileType.ALUMNI));

        assertThat(result.created()).isFalse();
        assertThat(result.profile().getProfileId()).isEqualTo(existing.getId());
        verify(alumni, never()).saveAndFlush(any());
    }

    @Test
    void aConcurrentCreationIsTreatedAsAlreadyExisting() {
        // retry de user-service arrivé en même temps : l'autre requête a gagné la contrainte d'unicité
        Alumni winner = Alumni.provisioned(USER, "Awa", "Diop", "awa.diop@uasz.sn");
        when(alumni.findByUserId(USER)).thenReturn(Optional.empty(), Optional.of(winner));
        when(alumni.saveAndFlush(any(Alumni.class))).thenThrow(new DataIntegrityViolationException("uq user_id"));

        ProfileProvisioningService.Result result = service.provision(request(ProvisionedProfileType.ALUMNI));

        assertThat(result.created()).isFalse();
        assertThat(result.profile().getProfileId()).isEqualTo(winner.getId());
    }

    @Test
    void createsTheStudentProfileWithTheAccountIdentity() {
        when(students.findByUserId(USER)).thenReturn(Optional.empty());

        ProfileProvisioningService.Result result = service.provision(request(ProvisionedProfileType.ETUDIANT));

        ArgumentCaptor<StudentProfileEntity> saved = ArgumentCaptor.forClass(StudentProfileEntity.class);
        verify(students).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(result.created()).isTrue();
        assertThat(result.profile().getUserId()).isEqualTo(USER);
        assertThat(result.profile().getProfileType()).isEqualTo(ProvisionedProfileType.ETUDIANT);
        verifyNoInteractions(alumni);
    }

    @Test
    void anExistingStudentProfileIsReturnedWithoutDuplicate() {
        StudentProfileEntity existing = new StudentProfileEntity(USER, "Awa", "Diop", "awa.diop@uasz.sn");
        when(students.findByUserId(USER)).thenReturn(Optional.of(existing));

        ProfileProvisioningService.Result result = service.provision(request(ProvisionedProfileType.ETUDIANT));

        assertThat(result.created()).isFalse();
        verify(students, never()).saveAndFlush(any());
    }
}
