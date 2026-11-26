
package com.uasz.alumni.user.security;

import static com.uasz.alumni.user.UserTestData.apiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final CurrentUser currentUser = new CurrentUser();

    @BeforeEach
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(UUID id, RoleName role) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "x@uasz.sn", role), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void anonymous_hasNoPrincipal_andRequireThrows401() {
        assertThat(currentUser.principal()).isEmpty();
        assertThat(currentUser.isAdmin()).isFalse();
        assertThatThrownBy(currentUser::require)
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }

    @Test
    void authenticated_exposesPrincipal() {
        loginAs(ME, RoleName.ALUMNI);

        assertThat(currentUser.require().id()).isEqualTo(ME);
        assertThat(currentUser.principal()).isPresent();
        assertThat(currentUser.isAdmin()).isFalse();
    }

    @Test
    void isAdmin_trueOnlyForAdministrateur() {
        loginAs(ME, RoleName.ADMINISTRATEUR);

        assertThat(currentUser.isAdmin()).isTrue();
    }

    @Test
    void requireSelfOrAdmin_allowsSelf() {
        loginAs(ME, RoleName.ETUDIANT);

        assertThatCode(() -> currentUser.requireSelfOrAdmin(ME)).doesNotThrowAnyException();
    }

    @Test
    void requireSelfOrAdmin_allowsAdminOnAnyone() {
        loginAs(ME, RoleName.ADMINISTRATEUR);

        assertThatCode(() -> currentUser.requireSelfOrAdmin(OTHER)).doesNotThrowAnyException();
    }

    @Test
    void requireSelfOrAdmin_refusesOtherNonAdmin() {
        loginAs(ME, RoleName.ENTREPRISE);

        assertThatThrownBy(() -> currentUser.requireSelfOrAdmin(OTHER)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireSelfOrAdmin_withoutAuthentication_throws401() {
        assertThatThrownBy(() -> currentUser.requireSelfOrAdmin(ME))
                .isInstanceOfSatisfying(ApiException.class, apiError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }
}
