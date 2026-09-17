package com.leadlens.democrm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leadlens.democrm.model.DemoLeadField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Demo CRM lead attributes, each carrying its own last-updated timestamp (F.16). */
@Repository
public interface DemoLeadFieldRepository extends JpaRepository<DemoLeadField, UUID> {

	List<DemoLeadField> findByTenantIdAndLeadId(String tenantId, String leadId);

	Optional<DemoLeadField> findByTenantIdAndLeadIdAndAttributeKey(
			String tenantId, String leadId, String attributeKey);
}
