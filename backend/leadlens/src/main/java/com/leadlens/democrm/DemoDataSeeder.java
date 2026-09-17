package com.leadlens.democrm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import com.leadlens.common.clock.BriefingClock;
import com.leadlens.democrm.model.DemoActivity;
import com.leadlens.democrm.model.DemoLead;
import com.leadlens.democrm.model.DemoLeadField;
import com.leadlens.democrm.model.DemoUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the Demo CRM with leads whose histories exercise every case the briefing engine has to
 * get right.
 *
 * <p>This is not decoration. A fixture of clean, English, internally-consistent records would
 * let a naive implementation look correct, and the cases below are precisely the ones that
 * separate a trustworthy briefing from a plausible-sounding one:
 *
 * <ul>
 *   <li><strong>Rahul Sharma (12345)</strong> - the main demo lead. Carries an unresolved
 *       pricing objection four months from nothing, a code-mixed Hinglish message, a call with
 *       a recording but no transcript, an unsynced financing requirement, a contradicted bhk
 *       field, and open commitments on both sides.</li>
 *   <li><strong>Empty lead (99001)</strong> - created hours ago with one missed call. The case
 *       that makes naive implementations hallucinate hardest, because the model wants to fill
 *       sections. A correct briefing here is almost entirely Missing Information (N.1).</li>
 *   <li><strong>Other tenant (70001)</strong> - exists only so the cross-tenant 403 test has
 *       something real to fail against.</li>
 * </ul>
 *
 * <p>Idempotent: re-running against a populated database changes nothing, so a restart during
 * the demo cannot duplicate the history.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	public static final String TENANT_ACME = "t-acme";
	public static final String TENANT_GLOBEX = "t-globex";

	public static final String USER_PRIYA = "u-priya";
	public static final String USER_ARJUN = "u-arjun";
	public static final String USER_MEERA = "u-meera";

	public static final String LEAD_RAHUL = "12345";
	public static final String LEAD_EMPTY = "99001";
	public static final String LEAD_OTHER_TENANT = "70001";

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private final DemoLeadRepository leads;
	private final DemoLeadFieldRepository fields;
	private final DemoActivityRepository activities;
	private final DemoUserRepository users;
	private final BriefingClock clock;

	public DemoDataSeeder(
			DemoLeadRepository leads,
			DemoLeadFieldRepository fields,
			DemoActivityRepository activities,
			DemoUserRepository users,
			BriefingClock clock) {
		this.leads = leads;
		this.fields = fields;
		this.activities = activities;
		this.users = users;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (leads.existsById(LEAD_RAHUL)) {
			log.debug("Demo CRM already seeded; leaving it alone");
			return;
		}

		seedUsers();
		seedRahul();
		seedEmptyLead();
		seedOtherTenantLead();

		log.info("Demo CRM seeded: leads {}, {}, {}", LEAD_RAHUL, LEAD_EMPTY, LEAD_OTHER_TENANT);
	}

	private void seedUsers() {
		users.save(user(USER_PRIYA, TENANT_ACME, "Priya Nair", "SALES_AGENT"));
		// Budget is masked for this role. That is what makes the "masked" kind of empty
		// demonstrable rather than theoretical (E.6).
		users.save(user(USER_ARJUN, TENANT_ACME, "Arjun Rao", "JUNIOR_AGENT"));
		users.save(user(USER_MEERA, TENANT_GLOBEX, "Meera Iyer", "SALES_AGENT"));
	}

	/**
	 * The main demo lead.
	 *
	 * <p>The timeline is built so that each of the plan's edge cases falls out of an ordinary
	 * sales history rather than being bolted on:
	 *
	 * <ul>
	 *   <li>The customer opened in Hinglish asking for 2BHK at 65L with an SBI loan. That single
	 *       message carries the code-mixed extraction case (R6) <em>and</em> the unsynced
	 *       financing case - "loan SBI se" is a financing requirement the CRM never captured in
	 *       its field (E.6, unsynced).</li>
	 *   <li>Requirements were later revised upward to 3BHK / 1.5-1.8 Cr, so the budget and bhk
	 *       facts genuinely supersede earlier ones (F.14).</li>
	 *   <li>At the 16 Sep site visit the customer floated a 1BHK investment unit instead - after
	 *       the bhk field was last set on 2 Sep. Field says 3BHK, the newer fact says 1BHK: the
	 *       contradicted case (F.16).</li>
	 *   <li>Decision maker was never mentioned by anyone: never-captured, not unsynced.</li>
	 * </ul>
	 */
	private void seedRahul() {
		Instant created = at(2026, 8, 20, 10, 15);

		leads.save(DemoLead.builder()
				.id(LEAD_RAHUL)
				.tenantId(TENANT_ACME)
				.assignedUserId(USER_PRIYA)
				.createdAt(created)
				.build());

		field(LEAD_RAHUL, "name", "Rahul Sharma", created);
		field(LEAD_RAHUL, "phone", "+91 98450 12345", created);
		field(LEAD_RAHUL, "email", "rahul.sharma@example.com", created);
		field(LEAD_RAHUL, "source", "Facebook campaign", created);
		field(LEAD_RAHUL, "campaign", "Whitefield Premium Q3", created);
		field(LEAD_RAHUL, "status", "Negotiation", at(2026, 9, 10, 17, 30));
		field(LEAD_RAHUL, "subStatus", "Payment discussion", at(2026, 9, 16, 12, 0));
		field(LEAD_RAHUL, "assignedTeam", "Bangalore East", created);

		// Requirement fields. bhk was last set on 2 Sep - the 16 Sep site visit note below is
		// newer, which is what makes the contradiction detectable.
		field(LEAD_RAHUL, "bhk", "3BHK", at(2026, 9, 2, 11, 0));
		field(LEAD_RAHUL, "location", "Whitefield", at(2026, 9, 2, 11, 0));
		// Matches the brief's own source-reference example: "Budget - Lead field updated on
		// 12 September".
		field(LEAD_RAHUL, "budget", "Rs 1.5-1.8 Cr", at(2026, 9, 12, 9, 45));
		field(LEAD_RAHUL, "timeline", "Within 3 months", at(2026, 9, 2, 11, 0));

		// Deliberately absent. "financing" is UNSYNCED (the Hinglish message states it);
		// "decisionMaker" is NEVER CAPTURED (nobody ever mentioned it). Rendering these two
		// identically would be the single most common failure in this section (E.6, F.10).

		// --- Activity history -------------------------------------------------------------

		activity(LEAD_RAHUL, "MESSAGE", "CUSTOMER", "WHATSAPP", at(2026, 8, 20, 10, 20),
				"2bhk chahiye, budget 65L tak, loan SBI se karwana hai. Whitefield side dekh rahe hain.",
				Map.of());

		activity(LEAD_RAHUL, "CALL", "AGENT", "PHONE", at(2026, 8, 22, 16, 5),
				"Discussed requirement in detail. Customer's family is growing, so he is now "
						+ "considering 3BHK instead of 2BHK. Budget can stretch if the project is ready to move in.",
				Map.of("durationSeconds", 445));

		activity(LEAD_RAHUL, "FIELD_UPDATE", "AGENT", "CRM", at(2026, 9, 2, 11, 0),
				"Requirement updated to 3BHK, Whitefield, possession within 3 months.",
				Map.of("attributeKey", "bhk", "oldValue", "2BHK", "newValue", "3BHK"));

		activity(LEAD_RAHUL, "PROPERTY_SHARED", "AGENT", "EMAIL", at(2026, 9, 5, 12, 30),
				"Shared Prestige Lakeside 3BHK floor plans and price sheet.",
				Map.of("projectId", "proj-a", "projectName", "Prestige Lakeside"));

		activity(LEAD_RAHUL, "CALL", "AGENT", "PHONE", at(2026, 9, 8, 15, 20),
				"Customer rejected Prestige Lakeside. Said the per-square-foot price is well above "
						+ "what he is willing to pay for that location.",
				Map.of("projectId", "proj-a", "durationSeconds", 312));

		activity(LEAD_RAHUL, "PROPERTY_SHARED", "AGENT", "EMAIL", at(2026, 9, 10, 10, 0),
				"Shared Brigade Cornerstone 3BHK options. Awaiting customer feedback.",
				Map.of("projectId", "proj-b", "projectName", "Brigade Cornerstone"));

		activity(LEAD_RAHUL, "STATUS_CHANGE", "AGENT", "CRM", at(2026, 9, 10, 17, 30),
				"Status moved from Qualified to Negotiation.",
				Map.of("oldStatus", "Qualified", "newStatus", "Negotiation"));

		// The brief's example: "Pricing objection - Call note added on 14 September".
		// Still unresolved, which is why the inclusion floor must keep it regardless of age.
		activity(LEAD_RAHUL, "CALL", "AGENT", "PHONE", at(2026, 9, 14, 11, 40),
				"Customer feels the current pricing is above their budget. Asked whether there is "
						+ "any flexibility on the quoted rate for Brigade Cornerstone. I said I would check "
						+ "with the sales head and revert.",
				Map.of("projectId", "proj-b", "durationSeconds", 508));

		// The brief's example: "Site visit commitment - Task created on 15 September".
		activity(LEAD_RAHUL, "TASK", "AGENT", "CRM", at(2026, 9, 15, 9, 0),
				"Arrange site visit at Brigade Cornerstone and share revised payment plan before the visit.",
				Map.of("dueAt", at(2026, 9, 17, 16, 0).toString(), "state", "OPEN"));

		// The brief's example: "Latest conversation - WhatsApp message dated 16 September".
		activity(LEAD_RAHUL, "MESSAGE", "CUSTOMER", "WHATSAPP", at(2026, 9, 16, 12, 0),
				"Can you share a payment plan with lower upfront? Also please confirm the site visit timing. "
						+ "I will bring my wife along.",
				Map.of());

		// A call that happened but was never summarised. Must surface as a gap, never be
		// dropped - otherwise an unread call and an uneventful call look identical (E.5).
		activity(LEAD_RAHUL, "CALL", "AGENT", "PHONE", at(2026, 9, 16, 18, 10),
				null,
				Map.of("recordingUrl", "https://demo-crm.local/recordings/call-88213.mp3",
						"durationSeconds", 274));

		// Newer than the bhk field (2 Sep). Field says 3BHK, this says 1BHK: CONTRADICTED.
		activity(LEAD_RAHUL, "NOTE", "AGENT", "CRM", at(2026, 9, 16, 19, 0),
				"During the walkthrough Rahul asked whether a 1BHK unit is available in the same tower "
						+ "as an investment purchase alongside the 3BHK. Wants pricing for 1BHK too.",
				Map.of());

		// Today's meeting - what the briefing is preparing the agent for.
		scheduled(LEAD_RAHUL, "SITE_VISIT", at(2026, 9, 17, 16, 0),
				"Walk through Brigade Cornerstone 3BHK show unit and present the revised payment plan.");
	}

	/**
	 * A lead created hours ago with one missed call and nothing else.
	 *
	 * <p>Run this case deliberately and early. It is the case that makes naive implementations
	 * hallucinate hardest, because a composer handed a campaign name and an assigned project
	 * will happily write "Customer is looking for a 3BHK in Whitefield" with no citations to
	 * invalidate - it never emitted any, it just wrote prose (F.1). A correct briefing here is
	 * almost entirely Missing Information, and that is a <em>good</em> briefing.
	 */
	private void seedEmptyLead() {
		Instant created = clock.now().minus(2, ChronoUnit.HOURS);

		leads.save(DemoLead.builder()
				.id(LEAD_EMPTY)
				.tenantId(TENANT_ACME)
				.assignedUserId(USER_PRIYA)
				.createdAt(created)
				.build());

		field(LEAD_EMPTY, "name", "Sneha Kulkarni", created);
		field(LEAD_EMPTY, "phone", "+91 99860 77120", created);
		field(LEAD_EMPTY, "source", "Portal enquiry", created);
		field(LEAD_EMPTY, "status", "New", created);
		// Everything else is genuinely unknown. No requirement, no budget, no location.

		activity(LEAD_EMPTY, "CALL", "AGENT", "PHONE", created.plus(30, ChronoUnit.MINUTES),
				null,
				Map.of("outcome", "NO_ANSWER", "durationSeconds", 0));
	}

	/** Exists so tenant isolation can be tested against a lead that genuinely belongs elsewhere. */
	private void seedOtherTenantLead() {
		Instant created = at(2026, 9, 1, 9, 0);

		leads.save(DemoLead.builder()
				.id(LEAD_OTHER_TENANT)
				.tenantId(TENANT_GLOBEX)
				.assignedUserId(USER_MEERA)
				.createdAt(created)
				.build());

		field(LEAD_OTHER_TENANT, "name", "Vikram Desai", created);
		field(LEAD_OTHER_TENANT, "status", "Qualified", created);
		field(LEAD_OTHER_TENANT, "budget", "Rs 90 L", created);

		activity(LEAD_OTHER_TENANT, "NOTE", "AGENT", "CRM", created.plus(1, ChronoUnit.HOURS),
				"Interested in 2BHK near Hebbal.", Map.of());
	}

	// --- helpers -------------------------------------------------------------------------

	private static Instant at(int year, int month, int day, int hour, int minute) {
		return LocalDate.of(year, month, day).atTime(LocalTime.of(hour, minute)).atZone(IST).toInstant();
	}

	private DemoUser user(String id, String tenantId, String name, String role) {
		return DemoUser.builder().id(id).tenantId(tenantId).displayName(name).role(role).build();
	}

	private void field(String leadId, String attributeKey, String value, Instant updatedAt) {
		String tenantId = LEAD_OTHER_TENANT.equals(leadId) ? TENANT_GLOBEX : TENANT_ACME;
		fields.save(DemoLeadField.builder()
				.tenantId(tenantId)
				.leadId(leadId)
				.attributeKey(attributeKey)
				.value(value)
				.updatedAt(updatedAt)
				.build());
	}

	private void activity(
			String leadId, String type, String actor, String channel,
			Instant occurredAt, String text, Map<String, Object> structured) {

		String tenantId = LEAD_OTHER_TENANT.equals(leadId) ? TENANT_GLOBEX : TENANT_ACME;
		activities.save(DemoActivity.builder()
				.tenantId(tenantId)
				.leadId(leadId)
				.type(type)
				.actor(actor)
				.channel(channel)
				.occurredAt(occurredAt)
				.text(text)
				.structured(new LinkedHashMap<>(structured))
				.scheduled(false)
				.updatedAt(occurredAt)
				.build());
	}

	private void scheduled(String leadId, String type, Instant scheduledAt, String purpose) {
		activities.save(DemoActivity.builder()
				.tenantId(TENANT_ACME)
				.leadId(leadId)
				.type(type)
				.actor("AGENT")
				.channel("IN_PERSON")
				.occurredAt(scheduledAt)
				.text(purpose)
				.purpose(purpose)
				.structured(new LinkedHashMap<>())
				.scheduled(true)
				.updatedAt(scheduledAt)
				.build());
	}
}
