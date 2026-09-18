package com.leadlens.crm.leadscrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@code leadscrm} adapter, bound to {@code leadlens.crm.leadscrm.*}.
 *
 * <p>Per the one-file-per-adapter convention (IMPLEMENTATION_PLAN.md G.7), these values come
 * from {@code backend/leadlens/.env.leadscrm}, not a shared config file.
 *
 * <p>Two independent credentials live here, for two different services this one adapter talks
 * to: Cognito authenticates against {@code leads-crm-backend}'s own {@code /leads} API; the
 * ai-sdk admin password authenticates against the {@code ai-query-sdk} instance mounted inside
 * that same backend, which is a separate auth domain with its own JWT (F.16's meeting-evidence
 * source - see {@link AiSdkMeetingClient}).
 *
 * @param baseUrl              {@code leads-crm-backend}'s base URL, including its context path
 *                             (e.g. {@code http://localhost:8090/leads-crm})
 * @param frontendOrigin       the origin the extension runs against (e.g.
 *                             {@code http://localhost:5173}), used by {@link LeadsCrmAdapter#supports}
 * @param cognitoRegion        the AWS region the user pool lives in
 * @param cognitoClientId      the public app client id (no secret - a browser/service can't hold one)
 * @param serviceUsername      a Cognito user this adapter authenticates as on the CRM's behalf.
 *                             Real per-user auth is Appendix 2 territory (Q5); a single service
 *                             identity is the accepted gap for a hackathon-scale integration.
 * @param servicePassword      that user's password
 * @param tenantId             the LeadLens-side tenant id this CRM's leads are scoped under -
 *                             a different concept from Cognito's own tenant claim, which this
 *                             adapter never reads: LeadLens's tenant model exists to keep two
 *                             LeadLens customers' data apart (C4), and a hackathon-scale
 *                             deployment with one CRM behind it needs exactly one such tenant
 * @param serviceUserId        the LeadLens-side user id {@link #serviceIdentities()} presents
 *                             as - distinct from {@link #serviceUsername}, which is Cognito's
 * @param crmTenantId          {@code leads-crm-backend}'s own tenant UUID for the service user
 *                             above - required because that backend's {@code TenantFilter}
 *                             resolves the tenant from the bearer token's own claim, but Cognito
 *                             access tokens (which is what gets sent as the bearer, not the ID
 *                             token) never carry custom attributes by default, so
 *                             {@code custom:tenantId} is invisible to it. Sent as the
 *                             {@code x-tenant-id} header, which that filter requires to parse as
 *                             a UUID. `[CORRECTED 2026-09-18]` - a first pass wrongly reused
 *                             {@link #tenantId} (LeadLens's own, non-UUID identity) for this
 * @param aiSdkAdminPassword   the ai-query-sdk instance's single admin password (see class doc)
 */
@ConfigurationProperties(prefix = "leadlens.crm.leadscrm")
public record LeadsCrmProperties(
		String baseUrl,
		String frontendOrigin,
		String cognitoRegion,
		String cognitoClientId,
		String serviceUsername,
		String servicePassword,
		String tenantId,
		String serviceUserId,
		String crmTenantId,
		String aiSdkAdminPassword) {

	public LeadsCrmProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8090/leads-crm" : baseUrl;
		frontendOrigin = (frontendOrigin == null || frontendOrigin.isBlank())
				? "http://localhost:5173" : frontendOrigin;
		tenantId = (tenantId == null || tenantId.isBlank()) ? "leadscrm-default" : tenantId;
		serviceUserId = (serviceUserId == null || serviceUserId.isBlank()) ? "leadscrm-service" : serviceUserId;
	}

	public boolean meetingsConfigured() {
		return aiSdkAdminPassword != null && !aiSdkAdminPassword.isBlank();
	}
}
