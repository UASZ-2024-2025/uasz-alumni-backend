
package com.uasz.alumni.user.service;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.apiError;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.spi.model.Gender;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.uasz.alumni.user.exceptions.ApiException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock UserAccountRepository users;
    @Mock RefreshTokenService refreshTokens;
    @Mock PasswordService passwords;
    @Mock PasswordEncoder encoder;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, refreshTokens, passwords, encoder, currentUser, audit, clock());
    }

    private void actingAs(UUID id, RoleName role) {
        when(currentUser.require()).thenReturn(new AuthenticatedUser(id, "acteur@uasz.sn", role));
    }

    private UserAccount target(RoleName role, UserStatus status) {
        UserAccount account = account(TARGET_ID, "awa.diop@uasz.sn", role, status);
        when(users.findById(TARGET_ID)).thenReturn(Optional.of(account));
        return account;
    }

    // ───── list ─────

    @Test
    void list_withoutFilters_usesDefaultPaging_sortedByCreationDesc() {
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(users).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_clampsPageAndSize() {
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, -5, 500);
        service.list(null, null, 2, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(users, org.mockito.Mockito.times(2)).findAll(pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isEqualTo(2);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void list_filtersByRole() {
        Page<UserAccount> page = new PageImpl<>(List.of(account(UserStatus.ACTIF)));
        when(users.findByRole(eq(RoleName.ENTREPRISE), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(RoleName.ENTREPRISE, null, 0, 10)).isSameAs(page);
    }

    @Test
    void list_filtersByStatus() {
        Page<UserAccount> page = new PageImpl<>(List.of());
        when(users.findByStatus(eq(UserStatus.SUSPENDU), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(null, UserStatus.SUSPENDU, 0, 10)).isSameAs(page);
    }

    @Test
    void list_filtersByRoleAndStatus() {
        Page<UserAccount> page = new PageImpl<>(List.of());
        when(users.findByRoleAndStatus(eq(RoleName.ALUMNI), eq(UserStatus.ACTIF), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(RoleName.ALUMNI, UserStatus.ACTIF, 0, 10)).isSameAs(page);
    }

    // ───── get ─────

    @Test
    void get_returnsUser_afterAuthorizationCheck() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);

        assertThat(service.get(TARGET_ID)).isSameAs(account);
        verify(currentUser).requireSelfOrAdmin(TARGET_ID);
    }

    @Test
    void get_forbidden_neverTouchesTheDatabase() {
        doThrow(new ForbiddenException()).when(currentUser).requireSelfOrAdmin(TARGET_ID);

        assertThatThrownBy(() -> service.get(TARGET_ID)).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(users);
    }

    @Test
    void get_unknownUser_throwsNotFound() {
        when(users.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TARGET_ID)).isInstanceOf(UserNotFoundException.class);
    }

    // ───── create ─────

    private static UserCreateRequest createRequest() {
        return new UserCreateRequest().lastName(" Diop ").firstName(" Awa ").email(" RH@Gainde.sn ")
                .role(RoleName.ENTREPRISE).phone(" +221770000000 ");
    }

    @Test
    void create_buildsPendingAccount_withRandomPassword_andSendsInvitation() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        when(users.existsByEmail("rh@gainde.sn")).thenReturn(false);
        when(encoder.encode(any(CharSequence.class))).thenReturn("RANDOMHASH");

        UserAccount created = service.create(createRequest());

        assertThat(created.getEmail()).isEqualTo("rh@gainde.sn");
        assertThat(created.getFirstName()).isEqualTo("Awa");
        assertThat(created.getLastName()).isEqualTo("Diop");
        assertThat(created.getRole()).isEqualTo(RoleName.ENTREPRISE);
        assertThat(created.getStatus()).isEqualTo(UserStatus.EN_ATTENTE);
        assertThat(created.getPhone()).isEqualTo("+221770000000");
        assertThat(created.getPasswordHash()).isEqualTo("RANDOMHASH");

        ArgumentCaptor<CharSequence> randomPassword = ArgumentCaptor.forClass(CharSequence.class);
        verify(encoder).encode(randomPassword.capture());
        assertThat(randomPassword.getValue().toString()).hasSizeGreaterThanOrEqualTo(40); // 256 bits aléatoires

        verify(users).saveAndFlush(created);
        verify(passwords).sendInvitation(created);
        verify(audit).record("USER_CREATED", ADMIN_ID, null, "role=ENTREPRISE");
    }

    @Test
    void create_canCreateAnAdministrator() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        when(users.existsByEmail("nouvel.admin@uasz.sn")).thenReturn(false);
        when(encoder.encode(any(CharSequence.class))).thenReturn("RANDOMHASH");

        UserAccount created = service.create(new UserCreateRequest().lastName("Ba").firstName("Moussa")
                .email("nouvel.admin@uasz.sn").role(RoleName.ADMINISTRATEUR));

        assertThat(created.getRole()).isEqualTo(RoleName.ADMINISTRATEUR);
        assertThat(created.getPhone()).isNull();
    }

    @Test
    void create_duplicateEmail_isRefused() {
        when(users.existsByEmail("rh@gainde.sn")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest())).isInstanceOf(EmailAlreadyUsedException.class);

        verify(users, never()).saveAndFlush(any(UserAccount.class));
        verifyNoInteractions(passwords);
    }

    @Test
    void create_raceOnUniqueConstraint_isReportedAsDuplicate() {
        when(users.existsByEmail("rh@gainde.sn")).thenReturn(false);
        when(encoder.encode(any(CharSequence.class))).thenReturn("RANDOMHASH");
        when(users.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(createRequest())).isInstanceOf(EmailAlreadyUsedException.class);

        verifyNoInteractions(passwords);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void create_blankFirstName_isRefusedWith400(String blank) {
        assertThatThrownBy(() -> service.create(createRequest().firstName(blank)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));

        verifyNoInteractions(users, encoder, passwords);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void create_blankLastName_isRefusedWith400(String blank) {
        assertThatThrownBy(() -> service.create(createRequest().lastName(blank)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));

        verifyNoInteractions(users, encoder, passwords);
    }

    // ───── update ─────

    @Test
    void update_selfUpdatesOnlyProvidedFields() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);
        actingAs(TARGET_ID, RoleName.ALUMNI);

        UserAccount result = service.update(TARGET_ID, new UserUpdateRequest().firstName("  Aïcha ")
                .phone("+221 77 000 00 00").gender(Gender.FEMALE).birthDate(LocalDate.of(1998, 5, 17)));

        assertThat(result.getFirstName()).isEqualTo("Aïcha");
        assertThat(result.getLastName()).isEqualTo("Diop"); // inchangé
        assertThat(result.getEmail()).isEqualTo("awa.diop@uasz.sn");
        assertThat(result.getPhone()).isEqualTo("+221 77 000 00 00");
        assertThat(result.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1998, 5, 17));
        verify(currentUser).requireSelfOrAdmin(TARGET_ID);
        verify(users).saveAndFlush(account);
        verify(audit).record("USER_UPDATED", TARGET_ID, TARGET_ID, null);
    }

    @Test
    void update_emptyPhoneClearsIt() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);
        account.updateProfile(null, null, "+221770000000", null, null);
        actingAs(TARGET_ID, RoleName.ALUMNI);

        service.update(TARGET_ID, new UserUpdateRequest().phone(""));

        assertThat(account.getPhone()).isNull();
    }

    @Test
    void update_blankName_isRejected() {
        target(RoleName.ALUMNI, UserStatus.ACTIF);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().firstName("   ")))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().lastName("")))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        verify(users, never()).saveAndFlush(any(UserAccount.class));
    }

    @Test
    void update_birthDateMustBeInThePast() {
        target(RoleName.ALUMNI, UserStatus.ACTIF);
        LocalDate today = LocalDate.ofInstant(NOW, java.time.ZoneOffset.UTC);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().birthDate(today)))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().birthDate(today.plusDays(1))))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void update_phoneLongerThan30_isRejected() {
        target(RoleName.ALUMNI, UserStatus.ACTIF);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().phone("1".repeat(31))))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void update_emailChange_byNonAdmin_isForbidden() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);
        when(currentUser.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().email("nouveau@uasz.sn")))
                .isInstanceOf(ForbiddenException.class);

        assertThat(account.getEmail()).isEqualTo("awa.diop@uasz.sn");
        verify(users, never()).saveAndFlush(any(UserAccount.class));
    }

    @Test
    void update_emailChange_byAdmin_isApplied_andNormalized() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        when(currentUser.isAdmin()).thenReturn(true);
        when(users.existsByEmail("nouveau@uasz.sn")).thenReturn(false);

        service.update(TARGET_ID, new UserUpdateRequest().email("  Nouveau@UASZ.sn "));

        assertThat(account.getEmail()).isEqualTo("nouveau@uasz.sn");
        verify(audit).record("USER_UPDATED", ADMIN_ID, TARGET_ID, null);
    }

    @Test
    void update_emailChange_toExistingEmail_isRefused() {
        target(RoleName.ALUMNI, UserStatus.ACTIF);
        when(currentUser.isAdmin()).thenReturn(true);
        when(users.existsByEmail("pris@uasz.sn")).thenReturn(true);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().email("pris@uasz.sn")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void update_sameEmailWithDifferentCase_isNotAnEmailChange() {
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);
        actingAs(TARGET_ID, RoleName.ALUMNI);

        service.update(TARGET_ID, new UserUpdateRequest().email("AWA.DIOP@uasz.sn"));

        assertThat(account.getEmail()).isEqualTo("awa.diop@uasz.sn");
        verify(currentUser, never()).isAdmin(); // pas besoin d'être admin : rien ne change
    }

    @Test
    void update_raceOnUniqueEmail_isReportedAsDuplicate() {
        target(RoleName.ALUMNI, UserStatus.ACTIF);
        when(users.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().firstName("Awa")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void update_forbiddenForOtherUsers() {
        doThrow(new ForbiddenException()).when(currentUser).requireSelfOrAdmin(TARGET_ID);

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().firstName("X")))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(users);
    }

    @Test
    void update_unknownUser_throwsNotFound() {
        when(users.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(TARGET_ID, new UserUpdateRequest().firstName("X")))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ───── updateStatus ─────

    @Test
    void updateStatus_suspend_revokesSessions_andAudits() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);

        UserAccount result = service.updateStatus(TARGET_ID, UserStatus.SUSPENDU);

        assertThat(result.getStatus()).isEqualTo(UserStatus.SUSPENDU);
        assertThat(account.getStatus()).isEqualTo(UserStatus.SUSPENDU);
        verify(refreshTokens).revokeAll(TARGET_ID);
        verify(audit).record("USER_STATUS_CHANGED", ADMIN_ID, TARGET_ID, "status=SUSPENDU");
    }

    @Test
    void updateStatus_reactivate_liftsLock_andKeepsSessionsUntouched() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ALUMNI, UserStatus.SUSPENDU);
        account.registerFailedLogin(1, Duration.ofMinutes(15), NOW);

        service.updateStatus(TARGET_ID, UserStatus.ACTIF);

        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIF);
        assertThat(account.isLockedAt(NOW)).isFalse();
        verify(refreshTokens, never()).revokeAll(any(UUID.class));
    }

    @Test
    void updateStatus_sameStatus_isNoOp() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ALUMNI, UserStatus.ACTIF);

        service.updateStatus(TARGET_ID, UserStatus.ACTIF);

        verifyNoInteractions(refreshTokens, audit);
    }

    @Test
    void updateStatus_onOwnAccount_isRefused() {
        actingAs(TARGET_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);

        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, UserStatus.INACTIF))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.CONFLICT, "CANNOT_MODIFY_SELF"));
    }

    @Test
    void updateStatus_toPendingOrNull_isRejected() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ALUMNI, UserStatus.ACTIF);

        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, UserStatus.EN_ATTENTE))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, null))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @Test
    void updateStatus_lastActiveAdministrator_cannotBeDeactivated() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatus(TARGET_ID, UserStatus.SUSPENDU))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.CONFLICT, "LAST_ADMIN"));

        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIF);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void updateStatus_administrator_canBeDeactivated_whenAnotherActiveOneExists() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(2L);

        service.updateStatus(TARGET_ID, UserStatus.INACTIF);

        assertThat(account.getStatus()).isEqualTo(UserStatus.INACTIF);
    }

    @Test
    void updateStatus_inactiveAdministrator_needsNoLastAdminCheck() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ADMINISTRATEUR, UserStatus.INACTIF);

        service.updateStatus(TARGET_ID, UserStatus.SUSPENDU);

        verify(users, never()).countByRoleAndStatus(any(RoleName.class), any(UserStatus.class));
    }

    // ───── delete ─────

    @Test
    void delete_removesAccount_andAudits() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ALUMNI, UserStatus.ACTIF);

        service.delete(TARGET_ID);

        verify(users).delete(account);
        verify(audit).record("USER_DELETED", ADMIN_ID, TARGET_ID, "email=awa.diop@uasz.sn");
    }

    @Test
    void delete_ownAccount_isRefused() {
        actingAs(TARGET_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.CONFLICT, "CANNOT_MODIFY_SELF"));

        verify(users, never()).delete(any(UserAccount.class));
    }

    @Test
    void delete_lastActiveAdministrator_isRefused() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOfSatisfying(com.uasz.alumni.user.exceptions.ApiException.class,
                        apiError(HttpStatus.CONFLICT, "LAST_ADMIN"));

        verify(users, never()).delete(any(UserAccount.class));
    }

    @Test
    void delete_administrator_isAllowed_whenAnotherActiveOneExists() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        UserAccount account = target(RoleName.ADMINISTRATEUR, UserStatus.ACTIF);
        when(users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF)).thenReturn(2L);

        service.delete(TARGET_ID);

        verify(users).delete(account);
    }

    @Test
    void delete_unknownUser_throwsNotFound() {
        actingAs(ADMIN_ID, RoleName.ADMINISTRATEUR);
        when(users.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(TARGET_ID)).isInstanceOf(UserNotFoundException.class);
    }
}
