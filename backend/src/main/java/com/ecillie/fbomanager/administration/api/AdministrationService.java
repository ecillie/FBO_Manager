package com.ecillie.fbomanager.administration.api;

import com.ecillie.fbomanager.administration.api.AdministrationModels.AirportSettings;
import com.ecillie.fbomanager.administration.api.AdministrationModels.Customer;
import com.ecillie.fbomanager.administration.api.AdministrationModels.CustomerFilter;
import com.ecillie.fbomanager.platform.api.ActorContext;
import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;

public interface AdministrationService {

	AirportSettings airportSettings(ActorContext actor);

	AirportSettings saveAirportSettings(AirportSettings settings, ActorContext actor);

	Customer customer(long customerId, ActorContext actor);

	RepositoryPage<Customer> customers(CustomerFilter filter, RepositoryPageRequest page, ActorContext actor);

	Customer saveCustomer(Customer customer, ActorContext actor);
}
