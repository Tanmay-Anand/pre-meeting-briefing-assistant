package com.leadlens.facts;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Fact access, tenant-scoped at the repository layer (see {@code EvidenceRepository} for why
 * there are no unscoped finders).
 *
 * <p>Facts are keyed to the lead rather than to a briefing, so the same rows serve every
 * briefing ever generated for that lead. {@link #findByEvidenceIdAndExtractorVersion} is the
 * extraction cache lookup: a cache hit is what turns a refresh into one model call instead of
 * four hundred (F.4).
 */
@Repository
public interface FactRepository extends JpaRepository<AtomicFact, UUID> {

	List<AtomicFact> findByTenantIdAndLeadRefOrderByOccurredAtAsc(String tenantId, String leadRef);

	List<AtomicFact> findByTenantIdAndLeadRefAndKindOrderByOccurredAtAsc(
			String tenantId, String leadRef, FactKind kind);

	List<AtomicFact> findByTenantIdAndLeadRefAndStatusOrderByOccurredAtAsc(
			String tenantId, String leadRef, FactStatus status);

	/** Extraction cache lookup. Non-empty means this evidence item never needs re-extracting. */
	List<AtomicFact> findByEvidenceIdAndExtractorVersion(UUID evidenceId, String extractorVersion);

	List<AtomicFact> findByTenantIdAndLeadRefAndAttributeKeyOrderByOccurredAtAsc(
			String tenantId, String leadRef, String attributeKey);

	void deleteByEvidenceIdAndExtractorVersion(UUID evidenceId, String extractorVersion);
}
