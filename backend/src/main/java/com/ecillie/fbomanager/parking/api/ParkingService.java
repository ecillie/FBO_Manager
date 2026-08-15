package com.ecillie.fbomanager.parking.api;

import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingArea;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingAreaPreference;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotFilter;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotPreference;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.List;

public interface ParkingService {

	ParkingArea saveArea(ParkingArea area, ActorContext actor);

	ParkingSpot saveSpot(ParkingSpot spot, ActorContext actor);

	ParkingSpot setSpotStatus(String spotCode, OperationalStatus status, ActorContext actor);

	ParkingAreaPreference saveAreaPreference(ParkingAreaPreference preference, ActorContext actor);

	ParkingSpotPreference saveSpotPreference(ParkingSpotPreference preference, ActorContext actor);

	ParkingSpot spot(String spotCode, ActorContext actor);

	List<ParkingArea> areaTree(ActorContext actor);

	RepositoryPage<ParkingSpot> spots(ParkingSpotFilter filter, RepositoryPageRequest page, ActorContext actor);
}
