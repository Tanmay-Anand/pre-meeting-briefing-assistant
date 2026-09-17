package com.leadlens.crm.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Demo CRM adapter, bound to {@code leadlens.crm.demo.*}.
 *
 * <p>Per the one-file-per-adapter convention (IMPLEMENTATION_PLAN.md G.7), these values come
 * from {@code backend/leadlens/.env.demo} rather than a shared config file. An adapter reads
 * only its own prefix and never another adapter's, and never {@code System.getenv()} directly -
 * the same boundary rule as the code, extended to configuration. That is what makes rotating a
 * leaked key for one CRM a change that touches nothing else.
 *
 * @param baseUrl where the Demo CRM's API lives
 * @param serviceUser the user LeadLens acts as when no end user is in play; real permission
 *                    checks always use the acting user's id instead
 */
@ConfigurationProperties(prefix = "leadlens.crm.demo")
public record DemoCrmProperties(String baseUrl, String serviceUser) {

	public DemoCrmProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : baseUrl;
	}
}
