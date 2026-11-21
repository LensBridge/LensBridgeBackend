package com.ibrasoft.lensbridge.repository.sql;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A repository slice on a real database: a throwaway SQLite file migrated by the same Flyway
 * scripts as production, with {@code ddl-auto=validate} so the entities are checked against
 * them too.
 * <p>
 * Mockito cannot say whether a conditional {@code @Modifying} query actually changes (or
 * refuses to change) a row, and that row count is the entire point of the single-use token
 * and command-status guards, so those repositories are tested here. SQLite is the dev
 * database; the JPQL used is dialect-neutral.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/repository-test-${random.uuid}.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
public @interface SqliteDataJpaTest {
}
