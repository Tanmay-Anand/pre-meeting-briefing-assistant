package com.leadlens.evidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Evidence access, tenant-scoped at the repository layer.
 *
 * <p><strong>Every finder takes a tenantId.</strong> There is deliberately no
 * {@code findByLeadRef(...)} convenience overload, and no unscoped finder exists even for
 * tests: tenant filtering that depends on each call site remembering to add a {@code where}
 * clause is filtering that will eventually be forgotten (C4, R14). Spring Data derives these
 * from the method names, so the constraint costs nothing to keep.
 */
@Repository
public interface EvidenceRepository extends JpaRepository<EvidenceItem, UUID> {

	List<EvidenceItem> findByTenantIdAndLeadRefOrderByOccurredAtAsc(String tenantId, String leadRef);

	List<EvidenceItem> findByTenantIdAndCrmKeyAndLeadRefOrderByOccurredAtAsc(
			String tenantId, String crmKey, String leadRef);

	Optional<EvidenceItem> findByTenantIdAndCrmKeyAndEvidenceKey(
			String tenantId, String crmKey, String evidenceKey);

	List<EvidenceItem> findByTenantIdAndIdIn(String tenantId, List<UUID> ids);

	long countByTenantIdAndLeadRef(String tenantId, String leadRef);
}
