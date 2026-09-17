package com.leadlens.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the escape hatch in {@link IntegrationTest}.
 *
 * <p>Integration tests skip themselves when Docker is unavailable so local development is not
 * blocked. That is fine on a laptop and unacceptable in CI, where a skipped suite would report
 * green while verifying nothing. This test turns the skip back into a failure wherever
 * {@code LEADLENS_REQUIRE_DOCKER=true} is set - see {@code .github/workflows/backend-ci.yml}.
 */
class DockerRequiredInCiTest {

	@Test
	void dockerIsAvailableWhereItIsRequired() {
		assumeTrue(isRequired(), "LEADLENS_REQUIRE_DOCKER is not set; Docker is optional here");

		assertThat(DockerClientFactory.instance().isDockerAvailable())
				.as("LEADLENS_REQUIRE_DOCKER=true, but no Docker daemon is reachable. "
						+ "Integration tests would silently skip and report a green build "
						+ "that verified nothing.")
				.isTrue();
	}

	@Test
	void webServerCanStartWhereIntegrationTestsAreRequired() {
		assumeTrue(isRequired(), "LEADLENS_REQUIRE_DOCKER is not set; this is a developer machine");

		assertThat(LoopbackNetworking.isAvailable())
				.as("Selector.open() fails here, so no embedded Tomcat can start and every "
						+ "web-server integration test would skip. Acceptable on a laptop, "
						+ "never in CI.")
				.isTrue();
	}

	private static boolean isRequired() {
		return Boolean.parseBoolean(System.getenv("LEADLENS_REQUIRE_DOCKER"));
	}
}
