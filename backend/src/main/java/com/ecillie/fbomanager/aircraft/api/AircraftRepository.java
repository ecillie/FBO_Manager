package com.ecillie.fbomanager.aircraft.api;

import com.ecillie.fbomanager.aircraft.api.AircraftModels.Aircraft;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftCategory;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftFilter;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftManufacturer;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModel;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModelKey;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftOperationType;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.List;
import java.util.Optional;

public interface AircraftRepository {

	AircraftCategory saveCategory(AircraftCategory category);

	AircraftOperationType saveOperationType(AircraftOperationType operationType);

	AircraftManufacturer saveManufacturer(AircraftManufacturer manufacturer);

	AircraftModel saveModel(AircraftModel model);

	Optional<AircraftModel> findModel(AircraftModelKey key);

	List<AircraftModel> findModelsByCategory(String categoryCode);

	Aircraft saveAircraft(Aircraft aircraft);

	Optional<Aircraft> findAircraft(String tailNumber);

	RepositoryPage<Aircraft> findAircraft(AircraftFilter filter, RepositoryPageRequest page);
}
