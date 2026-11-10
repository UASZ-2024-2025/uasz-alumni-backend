
package com.uasz.alumni.user.jwt;

import static com.uasz.alumni.user.UserTestData.NOW;
import static com.uasz.alumni.user.UserTestData.account;
import static com.uasz.alumni.user.UserTestData.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.AuthenticatedUser;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock JwtService jwt;
    @Mock UserAccountRepository users;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwt, users, clock());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validToken_andActiveUser_authenticatesWithDatabaseRole() throws Exception {
        UserAccount user = account(ID, "awa.diop@uasz.sn", RoleName.ENSEIGNANT, UserStatus.ACTIF);
        request.addHeader("Authorization", "Bearer   tok  ");
        when(jwt.parseUserId("tok")).thenReturn(Optional.of(ID));
        when(users.findById(ID)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNotNull();
        assertThat(authentication().getPrincipal())
                .isEqualTo(new AuthenticatedUser(ID, "awa.diop@uasz.sn", RoleName.ENSEIGNANT));
        assertThat(authentication().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ENSEIGNANT");
        assertThat(chain.getRequest()).isNotNull(); // la chaîne est toujours poursuivie
    }

    @Test
    void noAuthorizationHeader_leavesRequestAnonymous() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwt, users);
    }

    @Test
    void nonBearerScheme_isIgnored() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        verifyNoInteractions(jwt, users);
    }

    @Test
    void invalidToken_leavesRequestAnonymous() throws Exception {
        request.addHeader("Authorization", "Bearer bad");
        when(jwt.parseUserId("bad")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(users);
    }

    @Test
    void tokenOfDeletedUser_leavesRequestAnonymous() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwt.parseUserId("tok")).thenReturn(Optional.of(ID));
        when(users.findById(ID)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"EN_ATTENTE", "INACTIF", "SUSPENDU"})
    void nonActiveUser_isNotAuthenticated_evenWithValidToken(UserStatus status) throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwt.parseUserId("tok")).thenReturn(Optional.of(ID));
        when(users.findById(ID)).thenReturn(Optional.of(account(ID, "a@uasz.sn", RoleName.ALUMNI, status)));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
    }

    @Test
    void lockedUser_isNotAuthenticated() throws Exception {
        UserAccount user = account(ID, "a@uasz.sn", RoleName.ALUMNI, UserStatus.ACTIF);
        user.registerFailedLogin(1, Duration.ofMinutes(15), NOW);
        request.addHeader("Authorization", "Bearer tok");
        when(jwt.parseUserId("tok")).thenReturn(Optional.of(ID));
        when(users.findById(ID)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
    }
}
