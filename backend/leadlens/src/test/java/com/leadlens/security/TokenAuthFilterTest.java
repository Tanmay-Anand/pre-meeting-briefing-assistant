package com.leadlens.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TokenAuthFilter} is the only thing standing between {@code /api/briefings/**} /
 * {@code /api/crm/**} and an unauthenticated caller (IMPLEMENTATION_PLAN.md Phase 8,
 * "`[DONE 2026-09-17]` TokenAuthFilter + SecurityProperties"). A bug here is not "a feature is
 * slightly wrong" - it is "the gate everyone assumes exists is silently open, or silently closed
 * for legitimate traffic." Each case below maps to a specific way that gate could fail in a way
 * that would not be obvious from the outside:
 *
 * <ul>
 *   <li>if the dev-mode fallback (no {@code LEADLENS_API_TOKEN}) stopped calling the chain, every
 *       local dev/demo run would 404/hang with no explanation;
 *   <li>if a correctly authenticated request were rejected, the extension would be unusable the
 *       moment someone actually set the token;
 *   <li>if a missing/wrong token were let through - the one this suite weights the heaviest - the
 *       filter would look like it is enforcing auth while actually being a no-op, which is strictly
 *       worse than having no filter at all because it would pass a casual review;
 *   <li>if {@link TokenAuthFilter#shouldNotFilter} started guarding {@code /api/democrm/**}, that
 *       path would start requiring LeadLens's own token even though it plays the role of an
 *       external CRM's API and is supposed to enforce its own, independent auth
 *       ({@code DemoCrmController}) - see the class Javadoc on {@link TokenAuthFilter}.
 * </ul>
 *
 * <p>No mocking framework: {@link SecurityProperties} is a two-line record, and
 * {@link MockHttpServletRequest}/{@link MockHttpServletResponse} plus a hand-written
 * {@link FilterChain} are cheaper and more honest than mocking either.
 */
class TokenAuthFilterTest {

	private static final String GUARDED_PATH = "/api/briefings/123/talking-points";
	private static final String TOKEN = "s3cr3t-token";

	@Test
	@DisplayName("dev-mode fallback: unconfigured token lets every request through untouched")
	void unconfiguredTokenPassesRequestThrough() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(null));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called)
				.as("with no LEADLENS_API_TOKEN set, local dev/demo traffic must not be blocked")
				.isTrue();
		assertThat(response.getStatus())
				.as("the fallback must not touch the response at all")
				.isEqualTo(HttpServletResponseDefaultStatus());
	}

	@Test
	@DisplayName("blank token (whitespace only) is treated as unconfigured, same as null")
	void blankTokenAlsoTreatedAsUnconfigured() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties("   "));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called).isTrue();
	}

	@Test
	@DisplayName("configured + correct Bearer token: request passes through to the chain")
	void configuredWithCorrectTokenPassesThrough() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		request.addHeader("Authorization", "Bearer " + TOKEN);
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called).isTrue();
		assertThat(response.getStatus()).isEqualTo(HttpServletResponseDefaultStatus());
	}

	@Test
	@DisplayName("configured + missing Authorization header: 401 with the documented error body, chain never called")
	void configuredWithMissingHeaderIsRejected() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called)
				.as("SECURITY CRITICAL: a filter that still reaches the real handler on a missing "
						+ "token is far worse than a failing test - it means the gate is decorative")
				.isFalse();
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/json");
		assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"missing or invalid token\"}");
	}

	@Test
	@DisplayName("configured + wrong Bearer token: 401 with the documented error body, chain never called")
	void configuredWithWrongTokenIsRejected() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		request.addHeader("Authorization", "Bearer some-other-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called)
				.as("SECURITY CRITICAL: a wrong token must never reach the real handler")
				.isFalse();
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/json");
		assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"missing or invalid token\"}");
	}

	@Test
	@DisplayName("configured + non-Bearer Authorization header: 401, chain never called")
	void configuredWithNonBearerHeaderIsRejected() throws ServletException, IOException {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", GUARDED_PATH);
		request.addHeader("Authorization", TOKEN);
		MockHttpServletResponse response = new MockHttpServletResponse();
		RecordingFilterChain chain = new RecordingFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.called).isFalse();
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("shouldNotFilter: a path outside /api/briefings and /api/crm (e.g. /api/democrm) is never guarded")
	void shouldNotFilterExemptsUnrelatedApiPaths() {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));
		MockHttpServletRequest democrmRequest = new MockHttpServletRequest("GET", "/api/democrm/leads/123");

		assertThat(filter.shouldNotFilter(democrmRequest))
				.as("/api/democrm plays the role of an external CRM's own API and must enforce its "
						+ "own auth independently, not LeadLens's shared secret - see TokenAuthFilter's "
						+ "class Javadoc")
				.isTrue();
	}

	@Test
	@DisplayName("shouldNotFilter: /api/briefings and /api/crm are guarded")
	void shouldNotFilterGuardsBriefingsAndCrmPaths() {
		TokenAuthFilter filter = new TokenAuthFilter(new SecurityProperties(TOKEN));

		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/briefings/123"))).isFalse();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/crm/leads/123"))).isFalse();
	}

	/** {@code MockHttpServletResponse}'s own default status, spelled out so the assertion above reads intentionally. */
	private static int HttpServletResponseDefaultStatus() {
		return new MockHttpServletResponse().getStatus();
	}

	/** A {@link FilterChain} test double that only records whether it was invoked. */
	private static final class RecordingFilterChain implements FilterChain {
		private boolean called = false;

		@Override
		public void doFilter(ServletRequest request, ServletResponse response) {
			called = true;
		}
	}
}
