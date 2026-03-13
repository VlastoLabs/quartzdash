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
package  com.vlastolabs.quartzdash.api;

import com.vlastolabs.quartzdash.domain.ClusterNode;
import com.vlastolabs.quartzdash.domain.SchedulerStatus;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for scheduler-level operations.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    private final SchedulerReader schedulerReader;

    public SchedulerController(SchedulerReader schedulerReader) {
        this.schedulerReader = schedulerReader;
    }

    /**
     * Gets the overall scheduler status.
     *
     * @return scheduler status wrapped in ApiResponse
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SchedulerStatus>> getSchedulerStatus() {
        SchedulerStatus status = schedulerReader.getSchedulerStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Gets all cluster nodes.
     *
     * @return list of cluster nodes wrapped in ApiResponse
     */
    @GetMapping("/nodes")
    public ResponseEntity<ApiResponse<List<ClusterNode>>> getClusterNodes() {
        List<ClusterNode> nodes = schedulerReader.getClusterNodes();
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }
}