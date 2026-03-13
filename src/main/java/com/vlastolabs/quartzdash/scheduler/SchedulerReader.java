/*
 * Copyright 2026 VlastoLabs Software
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package  com.vlastolabs.quartzdash.scheduler;

import com.vlastolabs.quartzdash.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * Interface for reading scheduler state and job/trigger information.
 */
public interface SchedulerReader {

    SchedulerStatus getSchedulerStatus();

    List<ClusterNode> getClusterNodes();

    // Replaces getAllJobs(Optional<String> group)
    List<JobSummary> getAllJobs();
    List<JobSummary> getJobsByGroup(String group);

    Optional<JobSummary> getJobByKey(JobKey jobKey);

    // Replaces getAllTriggers(Optional<String> group, Optional<TriggerState> state)
    List<TriggerSummary> getAllTriggers();
    List<TriggerSummary> getTriggersByGroup(String group);
    List<TriggerSummary> getTriggersByState(TriggerState state);
    List<TriggerSummary> getTriggersByGroupAndState(String group, TriggerState state);

    Optional<TriggerSummary> getTriggerByKey(TriggerKey triggerKey);

    List<FiredTriggerEntry> getFiredTriggers();
}
