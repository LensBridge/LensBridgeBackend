package com.ibrasoft.lensbridge.migration;

import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.audit.AuditEntityType;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @Enumerated(STRING)} column in this schema is constrained by a {@code CHECK}
 * listing its legal values, and {@code ddl-auto=validate} does not inspect CHECK constraints.
 * So adding a constant to one of these enums and forgetting the migration compiles, starts,
 * passes every other test — and then fails at INSERT time, in whichever environment first
 * tries to write the new value.
 * <p>
 * That is not hypothetical. {@code LINK_TICKET_EVENT} and {@code UNLINK_TICKET_EVENT} were
 * added with V5 and never reached a CHECK constraint; every attempt to audit a ticket link
 * failed silently for as long as the feature has existed, because
 * {@code AdminAuditService.logAuditEvent} catches and logs rather than rethrowing — correctly,
 * since a failed audit write must not roll back the operation it describes. The three
 * {@code *_PRAYER_SPACE} actions repeated the mistake one migration later. Both were found by
 * reading the table, not by anything failing.
 * <p>
 * The check here is deliberately crude: it asserts the constant's name appears <em>somewhere</em>
 * in the dialect's migration text. It cannot tell you the value landed in the right constraint,
 * or that a later migration did not narrow it back out — that is review's job. What it catches
 * is the whole-cloth omission, which is the mistake that actually keeps happening.
 */
class MigrationEnumParityTest {

    private static final String SQLITE = "db/migration/sqlite";
    private static final String POSTGRESQL = "db/migration/postgresql";

    /** One case: a constant that must appear in one dialect's migrations. */
    record Case(String dialect, String enumName, String constant, String sql) {
        @Override
        public String toString() {
            return enumName + "." + constant + " in " + dialect;
        }
    }

    private static String migrationTextOf(String location) throws IOException, URISyntaxException {
        Path dir = Paths.get(Objects.requireNonNull(
                MigrationEnumParityTest.class.getClassLoader().getResource(location),
                "Missing migration directory: " + location).toURI());
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                combined.append(Files.readString(file)).append('\n');
            }
        }
        return combined.toString();
    }

    static Stream<Case> constrainedConstants() throws Exception {
        List<Case> cases = new ArrayList<>();
        for (String dialect : List.of(SQLITE, POSTGRESQL)) {
            String sql = migrationTextOf(dialect);
            for (AuditAction action : AuditAction.values()) {
                cases.add(new Case(dialect, "AuditAction", action.name(), sql));
            }
            for (AuditEntityType type : AuditEntityType.values()) {
                cases.add(new Case(dialect, "AuditEntityType", type.name(), sql));
            }
            for (Permission permission : Permission.values()) {
                cases.add(new Case(dialect, "Permission", permission.name(), sql));
            }
            for (Role role : Role.values()) {
                cases.add(new Case(dialect, "Role", role.name(), sql));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constrainedConstants")
    @DisplayName("every enum constant stored as a string is named in the migrations")
    void everyConstantAppearsInTheMigrations(Case testCase) {
        assertThat(testCase.sql())
                .as("%s.%s is persisted as a string into a CHECK-constrained column, but no "
                                + "migration under %s mentions it. Writing that value will fail the "
                                + "constraint at runtime — and for audit writes it fails silently. "
                                + "Add it to a migration in BOTH dialect directories.",
                        testCase.enumName(), testCase.constant(), testCase.dialect())
                .contains("'" + testCase.constant() + "'");
    }
}
