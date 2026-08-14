package com.ecillie.fbomanager.administration.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import java.time.ZoneId;

/** Framework-neutral administration persistence models. */
public final class AdministrationModels {

	private AdministrationModels() {
	}

	public record AirportSettings(String icaoCode, String name, String iataCode, ZoneId timezone, AuditMetadata audit) {
		public AirportSettings {
			icaoCode = NaturalKey.code(icaoCode);
			name = NaturalKey.name(name);
			iataCode = NaturalKey.optionalCode(iataCode);
			if (timezone == null) {
				throw new IllegalArgumentException("timezone must not be null");
			}
		}
	}

	public record Customer(Long customerId, String name, String phone, String email, String notes, boolean active,
			AuditMetadata audit) {
		public Customer {
			name = NaturalKey.name(name);
			phone = NaturalKey.optionalText(phone);
			email = NaturalKey.email(email);
		}
	}

	public record CustomerFilter(Boolean active, String nameContains) {
		public CustomerFilter {
			nameContains = nameContains == null ? null : NaturalKey.text(nameContains);
		}
	}
}
