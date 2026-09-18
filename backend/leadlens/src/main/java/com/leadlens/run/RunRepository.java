package com.leadlens.run;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunRepository extends JpaRepository<BriefingRun, UUID> {

	Optional<BriefingRun> findByTenantIdAndId(String tenantId, UUID id);
}
