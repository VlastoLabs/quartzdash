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

import java.util.List;

/**
 * Represents a summary of a Quartz job definition.
 *
 * @param key              the job key (group + name)
 * @param jobClass         the fully qualified job class name
 * @param description      optional job description
 * @param durable          true if job should be kept when no triggers reference it
 * @param requestsRecovery true if job requests recovery on scheduler restart
 * @param triggers         list of triggers associated with this job
 */
public record JobSummary(
        JobKey key,
        String jobClass,
        String description,
        boolean durable,
        boolean requestsRecovery,
        List<TriggerSummary> triggers
) {
}
