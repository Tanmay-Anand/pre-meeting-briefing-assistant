package com.leadlens;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Makes the CRM-agnostic claim provable, not just reviewed at merge time (Part J: "briefing,
 * facts and evidence must not import anything under crm.demo, crm.leadrat, crm.leadscrm,
 * crm.dom or crm.selection - only crm.CrmAdapter and crm.model" - Part K Phase 10 item 5).
 *
 * <p>This is the "one adapter class, engine unchanged" pitch turned into a build-breaking rule:
 * if a future adapter-specific detail leaks into the engine, this test fails before a judge or
 * a teammate has to notice by reading a diff.
 */
class ArchitectureTest {

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.leadlens");
	}

	@Test
	void engineNeverImportsAConcreteCrmAdapter() {
		ArchRule rule = noClasses()
				.that().resideInAnyPackage("com.leadlens.briefing..", "com.leadlens.facts..", "com.leadlens.evidence..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.leadlens.crm.demo..",
						"com.leadlens.crm.leadrat..",
						"com.leadlens.crm.leadscrm..",
						"com.leadlens.crm.dom..",
						"com.leadlens.crm.selection..")
				.because("the AI engine (briefing/facts/evidence) must only know about "
						+ "crm.CrmAdapter and crm.model - adding or rotating a CRM must never "
						+ "require touching extraction, selection, composition or grounding");

		rule.check(classes);
	}
}
