package com.ecillie.fbomanager.administration.internal.application;

import com.ecillie.fbomanager.administration.api.AdministrationModels.AirportSettings;
import com.ecillie.fbomanager.administration.api.AdministrationModels.Customer;
import com.ecillie.fbomanager.administration.api.AdministrationModels.CustomerFilter;
import com.ecillie.fbomanager.administration.api.AdministrationRepository;
import com.ecillie.fbomanager.administration.api.AdministrationService;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.Capability;
import com.ecillie.fbomanager.platform.api.DomainFailures;
import com.ecillie.fbomanager.platform.api.DomainNotFoundException;
import com.ecillie.fbomanager.platform.api.PersistenceFailure;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAdministrationService implements AdministrationService {

	private final AdministrationRepository repository;

	public DefaultAdministrationService(AdministrationRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public AirportSettings airportSettings(ActorContext actor) {
		actor.require(Capability.ADMINISTRATION_READ);
		return this.repository.findAirportSettings()
				.orElseThrow(() -> new DomainNotFoundException("AIRPORT_SETTINGS_NOT_FOUND",
						"Airport settings have not been configured."));
	}

	@Override
	@Transactional
	public AirportSettings saveAirportSettings(AirportSettings settings, ActorContext actor) {
		actor.require(Capability.ADMINISTRATION_WRITE);
		return write(() -> this.repository.saveAirportSettings(settings));
	}

	@Override
	@Transactional(readOnly = true)
	public Customer customer(long customerId, ActorContext actor) {
		actor.require(Capability.ADMINISTRATION_READ);
		return this.repository.findCustomer(customerId)
				.orElseThrow(() -> new DomainNotFoundException("CUSTOMER_NOT_FOUND", "The customer was not found.",
						Map.of("customerId", Long.toString(customerId))));
	}

	@Override
	@Transactional(readOnly = true)
	public RepositoryPage<Customer> customers(CustomerFilter filter, RepositoryPageRequest page, ActorContext actor) {
		actor.require(Capability.ADMINISTRATION_READ);
		return this.repository.findCustomers(filter, page);
	}

	@Override
	@Transactional
	public Customer saveCustomer(Customer customer, ActorContext actor) {
		actor.require(Capability.ADMINISTRATION_WRITE);
		return write(() -> this.repository.saveCustomer(customer));
	}

	private static <T> T write(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (PersistenceFailure failure) {
			throw DomainFailures.from(failure, "ADMINISTRATION_CONFLICT");
		}
	}
}
