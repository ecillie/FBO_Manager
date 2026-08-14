package com.ecillie.fbomanager.aircraft.api;

import com.ecillie.fbomanager.aircraft.api.AircraftModels.Aircraft;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftCategory;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftFilter;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftManufacturer;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModel;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModelKey;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftOperationType;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.List;

public interface AircraftService {

	AircraftCategory saveCategory(AircraftCategory category, ActorContext actor);

	AircraftOperationType saveOperationType(AircraftOperationType operationType, ActorContext actor);

	AircraftManufacturer saveManufacturer(AircraftManufacturer manufacturer, ActorContext actor);

	AircraftModel saveModel(AircraftModel model, ActorContext actor);

	AircraftModel model(AircraftModelKey key, ActorContext actor);

	List<AircraftModel> modelsByCategory(String categoryCode, ActorContext actor);

	Aircraft saveAircraft(Aircraft aircraft, ActorContext actor);

	Aircraft aircraft(String tailNumber, ActorContext actor);

	RepositoryPage<Aircraft> aircraft(AircraftFilter filter, RepositoryPageRequest page, ActorContext actor);
}
