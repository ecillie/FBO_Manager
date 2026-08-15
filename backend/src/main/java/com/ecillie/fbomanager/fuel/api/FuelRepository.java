package com.ecillie.fbomanager.fuel.api;

import com.ecillie.fbomanager.fuel.api.FuelModels.FuelLedgerEntry;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelTank;
import com.ecillie.fbomanager.fuel.api.FuelModels.FuelType;
import com.ecillie.fbomanager.fuel.api.FuelModels.LedgerFilter;
import com.ecillie.fbomanager.fuel.api.FuelModels.TankBalance;
import com.ecillie.fbomanager.fuel.api.FuelModels.TruckBalance;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.Optional;

public interface FuelRepository {

	FuelType saveType(FuelType type);

	Optional<FuelType> findType(String code);

	FuelTank saveTank(FuelTank tank);

	Optional<FuelTank> findTank(String name);

	FuelLedgerEntry append(FuelLedgerEntry entry);

	long nextTransferGroupId();

	Optional<TankBalance> findTankBalance(String tankName);

	Optional<TruckBalance> findTruckBalance(String vehicleIdentifier);

	Optional<TankBalance> lockTankBalance(String tankName);

	Optional<TruckBalance> lockTruckBalance(String vehicleIdentifier);

	RepositoryPage<TankBalance> findTankBalances(RepositoryPageRequest page);

	RepositoryPage<TruckBalance> findTruckBalances(RepositoryPageRequest page);

	RepositoryPage<FuelLedgerEntry> findLedger(LedgerFilter filter, RepositoryPageRequest page);
}
