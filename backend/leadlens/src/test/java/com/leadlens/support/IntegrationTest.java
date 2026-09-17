package com.leadlens.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

/**
 * Full-context test backed by a real Postgres in a container.
 *
 * <p>{@link EnabledIfDockerAvailable} means a developer without a running Docker daemon gets
 * skips rather than a wall of failures. That is a deliberate trade-off with a sharp edge: a
 * skipped test proves nothing, and a green local build that ran no integration tests is
 * exactly the "existence is not generation" confusion the plan warns about (F.9). So CI is
 * not allowed to take this path - {@link DockerRequiredInCiTest} fails the build if Docker is
 * missing wherever {@code LEADLENS_REQUIRE_DOCKER=true}, which the backend workflow sets.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import(PostgresTestcontainer.class)
@EnabledIfDockerAvailable
public @interface IntegrationTest {
}
