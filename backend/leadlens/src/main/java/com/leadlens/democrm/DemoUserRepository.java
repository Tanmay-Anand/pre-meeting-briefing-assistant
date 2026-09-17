package com.leadlens.democrm;

import java.util.Optional;

import com.leadlens.democrm.model.DemoUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Demo CRM users. Two tenants and two roles are seeded so Phase 8 has real data to enforce
 *  against - a cross-tenant 403 test needs a second tenant that actually exists. */
@Repository
public interface DemoUserRepository extends JpaRepository<DemoUser, String> {

	Optional<DemoUser> findByTenantIdAndId(String tenantId, String id);
}
