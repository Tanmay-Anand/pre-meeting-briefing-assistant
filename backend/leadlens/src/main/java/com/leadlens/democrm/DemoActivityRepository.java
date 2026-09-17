package com.leadlens.democrm;

import java.util.List;
import java.util.UUID;

import com.leadlens.democrm.model.DemoActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Demo CRM activities, past and scheduled. */
@Repository
public interface DemoActivityRepository extends JpaRepository<DemoActivity, UUID> {

	List<DemoActivity> findByTenantIdAndLeadIdAndScheduledOrderByOccurredAtAsc(
			String tenantId, String leadId, boolean scheduled);

	List<DemoActivity> findByTenantIdAndLeadIdOrderByOccurredAtAsc(String tenantId, String leadId);
}
