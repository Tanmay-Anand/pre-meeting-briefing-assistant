package com.leadlens.democrm;

import java.util.List;
import java.util.Optional;

import com.leadlens.democrm.model.DemoLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Demo CRM leads. Tenant-scoped finders only - see EvidenceRepository for the reasoning. */
@Repository
public interface DemoLeadRepository extends JpaRepository<DemoLead, String> {

	Optional<DemoLead> findByTenantIdAndId(String tenantId, String id);

	List<DemoLead> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
