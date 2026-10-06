package com.ibrasoft.lensbridge.repository.auth;

import com.ibrasoft.lensbridge.exception.RefreshTokenException;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.RefreshToken;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.model.auth.VerificationToken;
import com.ibrasoft.lensbridge.model.auth.VerificationToken.TokenType;
import com.ibrasoft.lensbridge.service.RefreshTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the hand-written JPQL (bulk updates and deletes, the constructor-expression projection)
 * and the refresh-token rotation against a real SQLite database built by the Flyway migrations.
 * Mockito tests cannot catch a query that Hibernate refuses to parse, or a bulk update that does
 * not do what its name says; those only surface at startup otherwise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/auth-repositories-query-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/sqlite",
        "spring.flyway.clean-disabled=false"
})
class AuthRepositoriesQueryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private VerificationTokenRepository verificationTokenRepository;
    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = new User("A", "B", unique, unique + "@example.com", "hash");
        user.addRole(Role.USER);
        user = userRepository.save(user);
    }

    private RefreshToken refreshToken(String hash, boolean revoked, Instant expiry, Instant created) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash).user(user).revoked(revoked)
                .expiryDate(expiry).createdDate(created).lastUsedDate(created)
                .build());
    }

    private RefreshTokenService service() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, userRepository);
        ReflectionTestUtils.setField(service, "refreshTokenDurationMs", 604_800_000L);
        ReflectionTestUtils.setField(service, "maxRefreshTokensPerUser", 5);
        return service;
    }

    @Test
    void revokeIfActiveChangesExactlyOneRowOnceThenNone() {
        String hash = UUID.randomUUID().toString();
        refreshToken(hash, false, Instant.now().plusSeconds(60), Instant.now());

        assertThat(refreshTokenRepository.revokeIfActive(hash)).isEqualTo(1);
        assertThat(refreshTokenRepository.revokeIfActive(hash)).isZero();
        assertThat(refreshTokenRepository.findByTokenHash(hash)).get().extracting(RefreshToken::isRevoked).isEqualTo(true);
    }

    @Test
    void rotateIssuesAReplacementAndReplayRevokesEverything() {
        RefreshTokenService service = service();
        RefreshTokenService.IssuedToken first = service.createRefreshToken(user.getId());

        RefreshTokenService.IssuedToken second = service.rotate(first.rawToken());

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        assertThat(refreshTokenRepository.findByUser_IdAndRevokedFalse(user.getId())).hasSize(1);

        // Replaying the rotated-out token is reuse: it fails and the replacement dies with it.
        assertThatThrownBy(() -> service.rotate(first.rawToken())).isInstanceOf(RefreshTokenException.class);
        assertThat(refreshTokenRepository.findByUser_IdAndRevokedFalse(user.getId())).isEmpty();
        // The revoked row is still there, so a second replay is also recognised as reuse.
        assertThat(refreshTokenRepository.countByUser_IdAndRevokedFalse(user.getId())).isZero();
        assertThatThrownBy(() -> service.rotate(first.rawToken()))
                .hasMessageContaining("revoked");
    }

    @Test
    void bulkDeletesRemoveOnlyWhatTheirNamesSay() {
        Instant now = Instant.now();
        refreshToken("expired", false, now.minusSeconds(10), now.minusSeconds(100));
        refreshToken("oldrevoked", true, now.plusSeconds(100), now.minusSeconds(86400L * 8));
        refreshToken("freshrevoked", true, now.plusSeconds(100), now);
        refreshToken("active", false, now.plusSeconds(100), now);

        assertThat(refreshTokenRepository.deleteExpiredForUser(user.getId(), now)).isEqualTo(1);
        assertThat(refreshTokenRepository.deleteRevokedCreatedBefore(now.minusSeconds(86400L * 7))).isEqualTo(1);
        assertThat(refreshTokenRepository.deleteExpired(now)).isZero();
        assertThat(refreshTokenRepository.findByTokenHash("freshrevoked")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("active")).isPresent();
        assertThat(refreshTokenRepository.revokeAllActiveForUser(user.getId())).isEqualTo(1);
    }

    @Test
    void verificationTokenBulkQueriesWork() {
        Instant now = Instant.now();
        VerificationToken older = verificationTokenRepository.save(VerificationToken.builder()
                .tokenHash("older").user(user).type(TokenType.PASSWORD_RESET)
                .createdAt(now).expiresAt(now.plusSeconds(600)).build());
        verificationTokenRepository.save(VerificationToken.builder()
                .tokenHash("verify").user(user).type(TokenType.EMAIL_VERIFICATION)
                .createdAt(now).expiresAt(now.plusSeconds(600)).build());

        assertThat(verificationTokenRepository.markOutstandingUsed(user, TokenType.PASSWORD_RESET, now)).isEqualTo(1);
        entityManager.clear();

        assertThat(verificationTokenRepository.findValidToken("older", TokenType.PASSWORD_RESET, now)).isEmpty();
        assertThat(verificationTokenRepository.findValidToken("verify", TokenType.EMAIL_VERIFICATION, now)).isPresent();
        assertThat(verificationTokenRepository.deleteExpiredOrUsed(now)).isEqualTo(1);
        assertThat(verificationTokenRepository.findById(older.getId())).isEmpty();
    }

    @Test
    void directPermissionsForManyUsersComeBackInOneQuery() {
        User other = new User("C", "D", "zz" + UUID.randomUUID().toString().substring(0, 6),
                UUID.randomUUID() + "@example.com", "hash");
        other.addDirectPermission(Permission.IAM_ROLE_GRANT);
        other = userRepository.save(other);
        user.addDirectPermission(Permission.BOARD_COMMAND_INSPECT);
        user.addDirectPermission(Permission.BOARD_POSTER_WRITE);
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        List<DirectPermissionRow> rows = userRepository.findDirectPermissionsForUsers(List.of(user.getId(), other.getId()));

        assertThat(rows).containsExactlyInAnyOrder(
                new DirectPermissionRow(user.getId(), Permission.BOARD_COMMAND_INSPECT),
                new DirectPermissionRow(user.getId(), Permission.BOARD_POSTER_WRITE),
                new DirectPermissionRow(other.getId(), Permission.IAM_ROLE_GRANT));
    }
}
