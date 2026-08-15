package com.ecillie.fbomanager.aircraft.internal.application;

import com.ecillie.fbomanager.aircraft.api.AircraftModels.Aircraft;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftCategory;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftFilter;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftManufacturer;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModel;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftModelKey;
import com.ecillie.fbomanager.aircraft.api.AircraftModels.AircraftOperationType;
import com.ecillie.fbomanager.aircraft.api.AircraftRepository;
import com.ecillie.fbomanager.aircraft.api.AircraftResourceAccess;
import com.ecillie.fbomanager.aircraft.api.AircraftService;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.DomainValidationException;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAircraftService implements AircraftService, AircraftResourceAccess {

	private final AircraftRepository repository;

	public DefaultAircraftService(AircraftRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public AircraftCategory saveCategory(AircraftCategory category, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_WRITE);
		return write(() -> this.repository.saveCategory(category));
	}

	@Override
	@Transactional
	public AircraftOperationType saveOperationType(AircraftOperationType operationType, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_WRITE);
		return write(() -> this.repository.saveOperationType(operationType));
	}

	@Override
	@Transactional
	public AircraftManufacturer saveManufacturer(AircraftManufacturer manufacturer, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_WRITE);
		return write(() -> this.repository.saveManufacturer(manufacturer));
	}

	@Override
	@Transactional
	public AircraftModel saveModel(AircraftModel model, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_WRITE);
		return write(() -> this.repository.saveModel(model));
	}

	@Override
	@Transactional(readOnly = true)
	public AircraftModel model(AircraftModelKey key, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_READ);
		return this.repository.findModel(key).orElseThrow(
				() -> new DomainNotFoundException("AIRCRAFT_MODEL_NOT_FOUND", "The aircraft model was not found."));
	}

	@Override
	@Transactional(readOnly = true)
	public List<AircraftModel> modelsByCategory(String categoryCode, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_READ);
		return this.repository.findModelsByCategory(categoryCode);
	}

	@Override
	@Transactional
	public Aircraft saveAircraft(Aircraft aircraft, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_WRITE);
		return write(() -> this.repository.saveAircraft(aircraft));
	}

	@Override
	@Transactional(readOnly = true)
	public Aircraft aircraft(String tailNumber, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_READ);
		return required(tailNumber, false);
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<Aircraft> aircraft(AircraftFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.AIRCRAFT_READ);
		return this.repository.findAircraft(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Aircraft lockActiveAircraft(String tailNumber) {
		Aircraft aircraft = required(tailNumber, true);
		if (!aircraft.active()) {
			throw new DomainValidationException("AIRCRAFT_INACTIVE", "The aircraft is not active.",
					Map.of("tailNumber", aircraft.tailNumber()));
		}
		return aircraft;
	}

	private Aircraft required(String tailNumber, boolean lock) {
		return (lock ? this.repository.lockAircraft(tailNumber) : this.repository.findAircraft(tailNumber))
				.orElseThrow(() -> new DomainNotFoundException("AIRCRAFT_NOT_FOUND", "The aircraft was not found.",
						Map.of("tailNumber", tailNumber)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "AIRCRAFT_CONFLICT");
		}
	}
}
