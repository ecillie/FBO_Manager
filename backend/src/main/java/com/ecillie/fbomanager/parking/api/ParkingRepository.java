package com.ecillie.fbomanager.parking.api;

import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingArea;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingAreaPreference;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotFilter;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotPreference;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.List;
import java.util.Optional;

public interface ParkingRepository {

	ParkingArea saveArea(ParkingArea area);

	ParkingSpot saveSpot(ParkingSpot spot);

	ParkingAreaPreference saveAreaPreference(ParkingAreaPreference preference);

	ParkingSpotPreference saveSpotPreference(ParkingSpotPreference preference);

	Optional<ParkingSpot> findSpot(String spotCode);

	/** Locks the row until the caller's transaction completes. */
	Optional<ParkingSpot> lockSpot(String spotCode);

	List<ParkingArea> findAreaTree();

	RepositoryPage<ParkingSpot> findSpots(ParkingSpotFilter filter, RepositoryPageRequest page);
}
