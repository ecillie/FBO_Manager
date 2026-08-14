package com.ecillie.fbomanager.administration.api;

import com.ecillie.fbomanager.administration.api.AdministrationModels.AirportSettings;
import com.ecillie.fbomanager.administration.api.AdministrationModels.Customer;
import com.ecillie.fbomanager.administration.api.AdministrationModels.CustomerFilter;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import java.util.Optional;

public interface AdministrationRepository {

	Optional<AirportSettings> findAirportSettings();

	AirportSettings saveAirportSettings(AirportSettings settings);

	Optional<Customer> findCustomer(long customerId);

	RepositoryPage<Customer> findCustomers(CustomerFilter filter, RepositoryPageRequest page);

	Customer saveCustomer(Customer customer);
}
