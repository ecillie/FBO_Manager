package com.ecillie.fbomanager.operations.api;

import com.ecillie.fbomanager.operations.api.OperationsModels.OperationsDashboard;
import com.ecillie.fbomanager.platform.api.ActorContext;
import java.time.LocalDate;

public interface OperationsService {

	OperationsDashboard dashboard(LocalDate operatingDate, ActorContext actor);
}
