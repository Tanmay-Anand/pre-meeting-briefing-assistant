package com.leadlens.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Skips a test that needs a real embedded web server when this machine cannot start one.
 *
 * <p>Same trade-off, and same safeguard, as {@link IntegrationTest}'s Docker condition: a
 * developer on a machine with broken loopback selectors is not blocked, but CI is not allowed
 * to take the skip. {@link DockerRequiredInCiTest} fails the build wherever
 * {@code LEADLENS_REQUIRE_DOCKER=true} and either Docker or selectors are unavailable.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(WebServerAvailableCondition.class)
public @interface EnabledIfWebServerCanStart {
}
