package com.ecillie.fbomanager.visits.api;

import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.visits.api.VisitModels.AircraftVisit;
import com.ecillie.fbomanager.visits.api.VisitModels.OperationalVisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitDetail;
import com.ecillie.fbomanager.visits.api.VisitModels.VisitFilter;
import java.util.Optional;

public interface VisitRepository {

	AircraftVisit save(AircraftVisit visit);

	Optional<AircraftVisit> find(long visitId);

	Optional<AircraftVisit> lock(long visitId);

	Optional<VisitDetail> findDetail(long visitId);

	Optional<OperationalVisitDetail> findOperationalDetail(long visitId);

	RepositoryPage<VisitDetail> findOperationalDetails(VisitFilter filter, RepositoryPageRequest page);
}
