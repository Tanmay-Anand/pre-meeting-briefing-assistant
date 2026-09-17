package com.leadlens.crm.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lead's current field state, already permission-filtered by the adapter.
 *
 * <p>This is what the Customer Snapshot section projects directly, with no model involved -
 * which is the structural answer to "how do you know it will not invent a budget" (D.2). It is
 * also one side of the field-versus-fact synchronisation check (F.16).
 *
 * <p>Fields are a map rather than a fixed record because CRMs differ in what they carry, and
 * the engine must not need to know any particular CRM's schema. The {@code attributeKey}s used
 * here are the same ones {@code FactKind.leadFieldAttribute()} returns, which is what lets the
 * two sides be compared at all.
 *
 * @param ref      which lead this is
 * @param fields   attributeKey -> value, including explicitly masked and absent entries
 */
public record LeadSnapshot(LeadRef ref, Map<String, FieldValue> fields) {

	public LeadSnapshot {
		fields = fields == null ? Map.of() : Map.copyOf(fields);
	}

	/** Never returns null: an unknown attribute is reported as absent, not as a missing key. */
	public FieldValue field(String attributeKey) {
		return fields.getOrDefault(attributeKey, FieldValue.absent());
	}

	public String displayName() {
		FieldValue name = field("name");
		return name.isPresent() ? name.value() : ref.leadRef();
	}

	public static Builder builder(LeadRef ref) {
		return new Builder(ref);
	}

	public static final class Builder {
		private final LeadRef ref;
		private final Map<String, FieldValue> fields = new LinkedHashMap<>();

		private Builder(LeadRef ref) {
			this.ref = ref;
		}

		public Builder field(String attributeKey, FieldValue value) {
			fields.put(attributeKey, value);
			return this;
		}

		public LeadSnapshot build() {
			return new LeadSnapshot(ref, fields);
		}
	}
}
