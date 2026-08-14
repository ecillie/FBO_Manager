package com.ecillie.fbomanager.parking.api;

import com.ecillie.fbomanager.platform.api.AuditMetadata;
import com.ecillie.fbomanager.platform.api.NaturalKey;
import com.ecillie.fbomanager.platform.api.OperationalStatus;

public final class ParkingModels {

	private ParkingModels() {
	}

	public record ParkingArea(String areaCode, String parentAreaCode, String name, String notes, boolean active,
			AuditMetadata audit) {
		public ParkingArea {
			areaCode = NaturalKey.code(areaCode);
			parentAreaCode = NaturalKey.optionalCode(parentAreaCode);
			name = NaturalKey.name(name);
		}
	}

	public record ParkingSpot(String spotCode, String parkingAreaCode, String name, OperationalStatus status,
			String notes, AuditMetadata audit) {
		public ParkingSpot {
			spotCode = NaturalKey.code(spotCode);
			parkingAreaCode = NaturalKey.code(parkingAreaCode);
			name = NaturalKey.name(name);
			if (status == null) {
				throw new IllegalArgumentException("status must not be null");
			}
		}
	}

	public record ParkingAreaPreference(String parkingAreaCode, String aircraftCategoryCode, short preferenceRank,
			AuditMetadata audit) {
		public ParkingAreaPreference {
			parkingAreaCode = NaturalKey.code(parkingAreaCode);
			aircraftCategoryCode = NaturalKey.code(aircraftCategoryCode);
			if (preferenceRank < 1) {
				throw new IllegalArgumentException("preferenceRank must be positive");
			}
		}
	}

	public record ParkingSpotPreference(String parkingSpotCode, String aircraftCategoryCode, short preferenceRank,
			AuditMetadata audit) {
		public ParkingSpotPreference {
			parkingSpotCode = NaturalKey.code(parkingSpotCode);
			aircraftCategoryCode = NaturalKey.code(aircraftCategoryCode);
			if (preferenceRank < 1) {
				throw new IllegalArgumentException("preferenceRank must be positive");
			}
		}
	}

	public record ParkingSpotFilter(String areaCode, OperationalStatus status, String categoryCode) {
		public ParkingSpotFilter {
			areaCode = NaturalKey.optionalCode(areaCode);
			categoryCode = NaturalKey.optionalCode(categoryCode);
		}
	}
}
