package com.ecillie.fbomanager.tasks.api;

import com.ecillie.fbomanager.platform.api.RepositoryPage;
import com.ecillie.fbomanager.platform.api.RepositoryPageRequest;
import com.ecillie.fbomanager.tasks.api.TaskModels.AirportTask;
import com.ecillie.fbomanager.tasks.api.TaskModels.TaskFilter;
import java.util.Optional;

public interface TaskRepository {

	AirportTask save(AirportTask task);

	Optional<AirportTask> find(long taskId);

	/** Locks a task before atomically assigning its worker and vehicle. */
	Optional<AirportTask> lockForAssignment(long taskId);

	RepositoryPage<AirportTask> findTasks(TaskFilter filter, RepositoryPageRequest page);
}
