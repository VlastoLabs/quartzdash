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

import java.time.Instant;

/**
 * Represents a currently executing (fired) trigger.
 *
 * @param entryId           the unique fired trigger entry ID
 * @param schedulerName     the scheduler instance name
 * @param jobName           the job name
 * @param jobGroup          the job group
 * @param triggerName       the trigger name
 * @param triggerGroup      the trigger group
 * @param firedAt           when the trigger was fired
 * @param scheduledFireTime the scheduled fire time
 * @param priority          the trigger priority
 * @param state             the fired trigger state
 * @param instanceName      the scheduler instance that fired the trigger
 */
public record FiredTriggerEntry(
        String entryId,
        String schedulerName,
        String jobName,
        String jobGroup,
        String triggerName,
        String triggerGroup,
        Instant firedAt,
        Instant scheduledFireTime,
        int priority,
        String state,
        String instanceName
) { }
