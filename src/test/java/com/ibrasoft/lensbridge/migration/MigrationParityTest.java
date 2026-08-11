package com.ibrasoft.lensbridge.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dev runs on SQLite and prod on Postgres, so every migration has to be written twice —
 * {@code db/migration/sqlite} and {@code db/migration/postgresql} — because the dialects'
 * DDL is not interchangeable ({@code uuid} vs {@code blob}, {@code bytea} vs {@code blob},
 * {@code timestamp(6) with time zone} vs {@code timestamp}).
 * <p>
 * Nothing about that is enforced by Flyway or the build. A migration added to only one
 * directory compiles, passes every other test, and runs fine locally — then either fails on
 * deploy or, worse, succeeds and leaves the other environment silently a version behind.
 * Since {@code spring.flyway.locations} uses {@code {vendor}}, neither environment can even
 * see the other's directory to notice.
 * <p>
 * This is a file-listing test rather than a schema-comparison one: it cannot tell you the two
 * scripts describe the same tables, only that you did not forget one. That is the mistake
 * worth catching automatically; the rest is review.
 */
class MigrationParityTest {

    private static final String SQLITE = "db/migration/sqlite";
    private static final String POSTGRESQL = "db/migration/postgresql";

    private static Path dir(String location) throws URISyntaxException {
        return Paths.get(Objects.requireNonNull(
                MigrationParityTest.class.getClassLoader().getResource(location),
                "Missing migration directory: " + location).toURI());
    }

    /** Version-and-description key: {@code V1__initial_schema.sql} -> {@code V1__initial_schema}. */
    private static List<String> migrationsIn(String location) throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.list(dir(location))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".sql"))
                    .map(n -> n.substring(0, n.length() - ".sql".length()))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Test
    void everyMigrationExistsForBothDatabases() throws Exception {
        assertThat(migrationsIn(SQLITE))
                .as("sqlite and postgresql must carry the same migrations, by version and description")
                .isEqualTo(migrationsIn(POSTGRESQL));
    }

    @Test
    void thereIsAtLeastOneMigration() throws Exception {
        assertThat(migrationsIn(SQLITE)).isNotEmpty();
    }

    /**
     * Two files sharing a version is a deploy-time Flyway error ("Found more than one migration
     * with version 2"), and it is easy to produce by branching: two people each add a V2.
     */
    @Test
    void versionsAreUniqueWithinEachDatabase() throws Exception {
        for (String location : List.of(SQLITE, POSTGRESQL)) {
            Map<String, Long> byVersion = migrationsIn(location).stream()
                    .map(name -> name.substring(0, name.indexOf("__")))
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

            assertThat(byVersion)
                    .as("duplicate migration version in %s", location)
                    .allSatisfy((version, count) -> assertThat(count).isEqualTo(1));
        }
    }
}
