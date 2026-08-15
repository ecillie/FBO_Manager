package com.ecillie.fbomanager.parking.internal.application;

import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingArea;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingAreaPreference;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpot;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotFilter;
import com.ecillie.fbomanager.parking.api.ParkingModels.ParkingSpotPreference;
import com.ecillie.fbomanager.parking.api.ParkingRepository;
import com.ecillie.fbomanager.parking.api.ParkingResourceAccess;
import com.ecillie.fbomanager.parking.api.ParkingService;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.DomainValidationException;
import com.ecillie.fbomanager.platform.api.OperationalStatus;
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
public class DefaultParkingService implements ParkingService, ParkingResourceAccess {

	private final ParkingRepository repository;

	public DefaultParkingService(ParkingRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ParkingArea saveArea(ParkingArea area, ActorContext actor) {
		actor.require(Capability.PARKING_WRITE);
		return write(() -> this.repository.saveArea(area));
	}

	@Override
	@Transactional
	public ParkingSpot saveSpot(ParkingSpot spot, ActorContext actor) {
		actor.require(Capability.PARKING_WRITE);
		return write(() -> this.repository.saveSpot(spot));
	}

	@Override
	@Transactional
	public ParkingSpot setSpotStatus(String spotCode, OperationalStatus status, ActorContext actor) {
		actor.require(Capability.PARKING_WRITE);
		ParkingSpot current = required(spotCode, true);
		ParkingSpot updated = new ParkingSpot(current.spotCode(), current.parkingAreaCode(), current.name(), status,
				current.notes(), current.audit());
		return write(() -> this.repository.saveSpot(updated));
	}

	@Override
	@Transactional
	public ParkingAreaPreference saveAreaPreference(ParkingAreaPreference preference, ActorContext actor) {
		actor.require(Capability.PARKING_WRITE);
		return write(() -> this.repository.saveAreaPreference(preference));
	}

	@Override
	@Transactional
	public ParkingSpotPreference saveSpotPreference(ParkingSpotPreference preference, ActorContext actor) {
		actor.require(Capability.PARKING_WRITE);
		return write(() -> this.repository.saveSpotPreference(preference));
	}

	@Override
	@Transactional(readOnly = true)
	public ParkingSpot spot(String spotCode, ActorContext actor) {
		actor.require(Capability.PARKING_READ);
		return required(spotCode, false);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ParkingArea> areaTree(ActorContext actor) {
		actor.require(Capability.PARKING_READ);
		return this.repository.findAreaTree();
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<ParkingSpot> spots(ParkingSpotFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.PARKING_READ);
		return this.repository.findSpots(filter, page);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ParkingSpot lockAvailableSpot(String spotCode) {
		ParkingSpot spot = required(spotCode, true);
		if (spot.status() != OperationalStatus.AVAILABLE) {
			throw new DomainValidationException("PARKING_SPOT_OUT_OF_SERVICE", "The parking spot is out of service.",
					Map.of("parkingSpotCode", spot.spotCode()));
		}
		return spot;
	}

	private ParkingSpot required(String spotCode, boolean lock) {
		return (lock ? this.repository.lockSpot(spotCode) : this.repository.findSpot(spotCode))
				.orElseThrow(() -> new DomainNotFoundException("PARKING_SPOT_NOT_FOUND",
						"The parking spot was not found.", Map.of("parkingSpotCode", spotCode)));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "PARKING_CONFLICT");
		}
	}
}
