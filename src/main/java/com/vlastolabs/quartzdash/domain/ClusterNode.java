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
 * Represents a cluster node (Quartz scheduler instance) in a clustered environment.
 *
 * @param instanceId      the unique instance identifier
 * @param lastCheckin     the last heartbeat timestamp
 * @param checkinInterval the configured check-in interval in milliseconds
 * @param alive           computed: true if lastCheckin + 2*interval > now
 */
public record ClusterNode(
        String instanceId,
        Instant lastCheckin,
        long checkinInterval,
        boolean alive
) {
    /**
     * Creates a ClusterNode with computed alive status.
     */
    public ClusterNode(String instanceId, Instant lastCheckin, long checkinInterval) {
        this(instanceId, lastCheckin, checkinInterval, computeAlive(lastCheckin, checkinInterval));
    }

    private static boolean computeAlive(Instant lastCheckin, long checkinInterval) {
        if (lastCheckin == null || checkinInterval <= 0) {
            return false;
        }
        Instant threshold = Instant.now().minusMillis(checkinInterval * 2L);
        return lastCheckin.isAfter(threshold);
    }
}
