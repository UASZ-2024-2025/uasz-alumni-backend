package com.uasz.alumni.user.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Rejoue les migrations Flyway (V1, V2) sur un vrai PostgreSQL, dans un schéma jetable, et vérifie
 * les contraintes dont dépend le code (dont {@code uq_users_email}, lu par RegistrationService).
 *
 * <p>Ce test tourne seulement si SPRING_DATASOURCE_URL est défini (base de dev : {@code docker compose up -d postgres},
 * variables du .env chargées). Sinon il est ignoré. Il n'écrit que dans un schéma créé puis supprimé.
 */
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class UsersMigrationPostgresTest {

    private static final String SCHEMA = "migration_test_" + UUID.randomUUID().toString().replace("-", "");

    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void migrate() {
        url = System.getenv("SPRING_DATASOURCE_URL");
        user = System.getenv("SPRING_DATASOURCE_USERNAME");
        password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        Flyway.configure().dataSource(url, user, password).schemas(SCHEMA).load().migrate();
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void createsUsersAndTokenTables() throws SQLException {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? "
                        + "AND table_name IN ('users', 'email_confirmation_tokens')")) {
            ps.setString(1, SCHEMA);
            var rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void duplicateEmailViolatesTheNamedUniqueConstraint() throws SQLException {
        try (Connection c = connection()) {
            insertUser(c, "doublon@uasz.sn", "Awa", "ALUMNI", "EN_ATTENTE");
            assertThatThrownBy(() -> insertUser(c, "doublon@uasz.sn", "Awa", "ALUMNI", "EN_ATTENTE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_users_email");
        }
    }

    @Test
    void rejectsUnknownRoleAndStatus() throws SQLException {
        try (Connection c = connection()) {
            assertThatThrownBy(() -> insertUser(c, "role@uasz.sn", "Awa", "PIRATE", "EN_ATTENTE"))
                    .hasMessageContaining("ck_users_role");
            assertThatThrownBy(() -> insertUser(c, "statut@uasz.sn", "Awa", "ALUMNI", "INCONNU"))
                    .hasMessageContaining("ck_users_status");
        }
    }

    @Test
    void rejectsFirstNameLongerThan100Characters() throws SQLException {
        try (Connection c = connection()) {
            assertThatThrownBy(() -> insertUser(c, "long@uasz.sn", "A".repeat(101), "ALUMNI", "EN_ATTENTE"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void tokensAreUniqueAndDeletedWithTheirUser() throws SQLException {
        try (Connection c = connection()) {
            UUID userId = insertUser(c, "jeton@uasz.sn", "Awa", "ALUMNI", "EN_ATTENTE");
            String hash = "a".repeat(64);
            insertToken(c, userId, hash);
            assertThatThrownBy(() -> insertToken(c, userId, hash)).hasMessageContaining("uq_email_confirmation_tokens_hash");

            try (PreparedStatement delete = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
                delete.setObject(1, userId);
                delete.executeUpdate();
            }
            try (Statement s = c.createStatement()) {
                var rs = s.executeQuery("SELECT count(*) FROM email_confirmation_tokens WHERE user_id = '" + userId + "'");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private static Connection connection() throws SQLException {
        Connection c = DriverManager.getConnection(url, user, password);
        try (Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + SCHEMA);
        }
        return c;
    }

    private static UUID insertUser(Connection c, String email, String firstName, String role, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (id, email, password_hash, first_name, last_name, role, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'HASH', ?, 'Diop', ?, ?, now(), now())")) {
            ps.setObject(1, id);
            ps.setString(2, email);
            ps.setString(3, firstName);
            ps.setString(4, role);
            ps.setString(5, status);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertToken(Connection c, UUID userId, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO email_confirmation_tokens (id, user_id, token_hash, expires_at, created_at) "
                        + "VALUES (?, ?, ?, now() + interval '1 day', now())")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, hash);
            ps.executeUpdate();
        }
    }
}
