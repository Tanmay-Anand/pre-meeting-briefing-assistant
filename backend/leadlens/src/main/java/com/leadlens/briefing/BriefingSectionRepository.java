package com.leadlens.briefing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Stored sections of a briefing, frozen as the agent saw them. */
@Repository
public interface BriefingSectionRepository extends JpaRepository<BriefingSectionEntity, UUID> {

	List<BriefingSectionEntity> findByBriefingId(UUID briefingId);
}
