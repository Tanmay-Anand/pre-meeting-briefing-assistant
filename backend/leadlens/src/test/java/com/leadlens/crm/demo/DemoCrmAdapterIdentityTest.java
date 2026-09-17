package com.leadlens.crm.demo;

import java.net.URI;

import com.leadlens.crm.model.LeadRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lead identification, which is the one thing the extension must never get wrong.
 *
 * <p>A mis-identified lead means briefing the agent about the wrong customer - worse than no
 * briefing at all. These tests pin the two halves of that guarantee: identity comes from the
 * URL, and a URL that does not identify a lead returns empty rather than a guess (ledger #4).
 *
 * <p>No HTTP happens here, so no server is needed.
 */
class DemoCrmAdapterIdentityTest {

	private final DemoCrmAdapter adapter = new DemoCrmAdapter(
			RestClient.builder(),
			new DemoCrmProperties("http://localhost:8080", "u-priya"));

	@Test
	void resolvesLeadIdFromLeadUrl() {
		assertThat(adapter.resolveLead(URI.create("http://localhost:5174/leads/12345")))
				.contains(new LeadRef("demo", "12345"));
	}

	@Test
	void resolvesLeadIdWhenTheUrlHasMoreAfterIt() {
		assertThat(adapter.resolveLead(URI.create("http://localhost:5174/leads/12345/activities/9")))
				.contains(new LeadRef("demo", "12345"));
	}

	@Test
	@DisplayName("a non-lead page yields empty rather than a guess")
	void returnsEmptyForNonLeadPages() {
		assertThat(adapter.resolveLead(URI.create("http://localhost:5174/"))).isEmpty();
		assertThat(adapter.resolveLead(URI.create("http://localhost:5174/reports/weekly"))).isEmpty();
		assertThat(adapter.resolveLead(URI.create("http://localhost:5174/leads"))).isEmpty();
	}

	@Test
	void toleratesNullAndPathlessUrls() {
		assertThat(adapter.resolveLead(null)).isEmpty();
		assertThat(adapter.supports(null)).isFalse();
	}

	@Test
	@DisplayName("only claims pages it actually has an adapter for")
	void supportsOnlyItsOwnHost() {
		assertThat(adapter.supports(URI.create("http://localhost:5174/leads/1"))).isTrue();
		assertThat(adapter.supports(URI.create("http://localhost:3000/leads/1"))).isFalse();
		assertThat(adapter.supports(URI.create("https://crm.example.com/leads/1"))).isFalse();
	}
}
