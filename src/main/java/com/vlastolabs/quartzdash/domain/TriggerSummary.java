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

/**
 * Represents a summary of a Quartz trigger.
 *
 * @param key                the trigger key (group + name)
 * @param state              the current trigger state
 * @param type               the trigger type (CRON, SIMPLE, etc.)
 * @param cronExpression     the cron expression (null if not a CRON trigger)
 * @param previousFireTime   the last time this trigger fired
 * @param nextFireTime       the next scheduled fire time
 * @param misfireInstruction the misfire instruction code
 * @param misfired           computed: true if state is ERROR or nextFireTime is significantly past
 */
public record TriggerSummary(
        TriggerKey key,
        TriggerState state,
        TriggerType type,
        String cronExpression,
        Instant previousFireTime,
        Instant nextFireTime,
        int misfireInstruction,
        boolean misfired
) {
    /**
     * Creates a TriggerSummary with computed misfired status.
     */
    public TriggerSummary(TriggerKey key, TriggerState state, TriggerType type,
                          String cronExpression, Instant previousFireTime, Instant nextFireTime,
                          int misfireInstruction) {
        this(key, state, type, cronExpression, previousFireTime, nextFireTime,
                misfireInstruction, computeMisfired(state, nextFireTime));
    }

    private static boolean computeMisfired(TriggerState state, Instant nextFireTime) {
        if (state == TriggerState.ERROR) {
            return true;
        }
        if (nextFireTime == null) {
            return false;
        }
        // Consider misfired if next fire time is more than 60 seconds in the past
        return nextFireTime.isBefore(Instant.now().minusSeconds(60));
    }
}
