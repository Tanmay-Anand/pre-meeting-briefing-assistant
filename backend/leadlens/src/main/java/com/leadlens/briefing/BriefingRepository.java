package com.leadlens.briefing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Briefings, tenant-scoped. Superseded versions are retained, never deleted (D.5). */
@Repository
public interface BriefingRepository extends JpaRepository<Briefing, UUID> {

	Optional<Briefing> findByTenantIdAndId(String tenantId, UUID id);

	/** The newest briefing for this lead and user; masking is per-role, so the user matters. */
	Optional<Briefing> findFirstByTenantIdAndCrmKeyAndLeadRefAndGeneratedForOrderByCreatedAtDesc(
			String tenantId, String crmKey, String leadRef, String generatedFor);

	List<Briefing> findByTenantIdAndCrmKeyAndLeadRefOrderByCreatedAtDesc(
			String tenantId, String crmKey, String leadRef);
}
