package com.leadlens.democrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Demo CRM user. Seeded with two roles and two tenants so the permission and tenant
 * enforcement of Phase 8 has something real to enforce against - a cross-tenant 403 test needs
 * a second tenant to actually exist.
 */
@Entity
@Table(name = "demo_users", indexes = @Index(name = "ix_demo_user_tenant", columnList = "tenant_id"))
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoUser {

	@Id
	@Column(name = "id", length = 128)
	private String id;

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "display_name", nullable = false, length = 256)
	private String displayName;

	/** "SALES_AGENT" sees everything on their leads; "JUNIOR_AGENT" has budget masked. */
	@Column(name = "role", nullable = false, length = 64)
	private String role;
}
