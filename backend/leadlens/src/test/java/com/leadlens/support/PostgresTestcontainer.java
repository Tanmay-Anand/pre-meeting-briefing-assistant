package com.leadlens.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres for integration tests.
 *
 * <p>The schema is created by Hibernate ({@code ddl-auto: update}) rather than by a migration
 * tool - see IMPLEMENTATION_PLAN.md 0.3 and E.7. That makes a real Postgres the only honest
 * way to test persistence: an in-memory database would validate against a different dialect
 * than the one production uses, so a column type Hibernate generates incorrectly for Postgres
 * would pass the test suite and fail at runtime.
 *
 * <p>The container is reused across the whole test run because it is a singleton bean in the
 * shared Spring test context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}
}
