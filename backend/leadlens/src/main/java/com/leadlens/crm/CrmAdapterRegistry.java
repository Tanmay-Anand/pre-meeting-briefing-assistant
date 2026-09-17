package com.leadlens.crm;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Looks up the adapter for a CRM key or a page URL.
 *
 * <p>Spring injects every {@link CrmAdapter} on the classpath, so registering a new CRM is
 * adding a {@code @Component} - there is no central list to edit, and therefore no central list
 * to forget to edit.
 */
@Component
public class CrmAdapterRegistry {

	private final Map<String, CrmAdapter> byKey;

	public CrmAdapterRegistry(List<CrmAdapter> adapters) {
		Map<String, CrmAdapter> map = new LinkedHashMap<>();
		for (CrmAdapter adapter : adapters) {
			CrmAdapter clash = map.put(adapter.crmKey(), adapter);
			if (clash != null) {
				throw new IllegalStateException(
						"Two adapters claim crmKey '%s': %s and %s".formatted(
								adapter.crmKey(),
								clash.getClass().getName(),
								adapter.getClass().getName()));
			}
		}
		this.byKey = Map.copyOf(map);
	}

	/**
	 * The adapter for this key.
	 *
	 * @throws UnknownCrmException when no adapter is registered - an unknown key is a caller
	 *         error worth surfacing, not something to silently fall back from
	 */
	public CrmAdapter require(String crmKey) {
		CrmAdapter adapter = byKey.get(crmKey);
		if (adapter == null) {
			throw new UnknownCrmException(crmKey, byKey.keySet());
		}
		return adapter;
	}

	/** The adapter that handles this page URL, or empty when none does. */
	public Optional<CrmAdapter> forUrl(URI pageUrl) {
		return byKey.values().stream().filter(adapter -> adapter.supports(pageUrl)).findFirst();
	}

	public List<CrmAdapter> all() {
		return List.copyOf(byKey.values());
	}

	public static class UnknownCrmException extends RuntimeException {
		public UnknownCrmException(String crmKey, java.util.Set<String> known) {
			super("No CRM adapter registered for '%s'. Known adapters: %s".formatted(crmKey, known));
		}
	}
}
