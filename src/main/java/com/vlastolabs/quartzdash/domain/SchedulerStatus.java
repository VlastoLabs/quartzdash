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
package  com.vlastolabs.quartzdash.domain;

import java.time.Instant;
import java.util.List;

/**
 * Represents the overall status of a Quartz Scheduler instance.
 *
 * @param schedulerName the scheduler name
 * @param instanceId    the unique instance identifier
 * @param state         the scheduler state (STARTED, STANDBY, SHUTDOWN)
 * @param totalJobs     total number of jobs registered
 * @param totalTriggers total number of triggers registered
 * @param runningJobs   number of currently executing jobs
 * @param clusterNodes  list of cluster nodes (empty if not clustered)
 * @param polledAt      timestamp when this status was polled
 */
public record SchedulerStatus(
        String schedulerName,
        String instanceId,
        SchedulerState state,
        int totalJobs,
        int totalTriggers,
        int runningJobs,
        List<ClusterNode> clusterNodes,
        Instant polledAt
) {
}
