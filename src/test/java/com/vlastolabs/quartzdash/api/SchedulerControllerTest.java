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
import com.vlastolabs.quartzdash.domain.SchedulerState;
import com.vlastolabs.quartzdash.domain.SchedulerStatus;
import com.vlastolabs.quartzdash.exception.ConnectionException;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SchedulerController.class, GlobalExceptionHandler.class})
class SchedulerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SchedulerReader schedulerReader;

    // --- GET /api/v1/scheduler/status ---

    @Test
    void getSchedulerStatus_returnsStatus() throws Exception {
        SchedulerStatus status = new SchedulerStatus("MyScheduler", "instance-1",
                SchedulerState.STARTED, 10, 5, 2, List.of(), Instant.now());
        when(schedulerReader.getSchedulerStatus()).thenReturn(status);

        mockMvc.perform(get("/api/v1/scheduler/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedulerName", is("MyScheduler")))
                .andExpect(jsonPath("$.data.instanceId", is("instance-1")))
                .andExpect(jsonPath("$.data.state", is("STARTED")))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getSchedulerStatus_connectionError_returns503() throws Exception {
        when(schedulerReader.getSchedulerStatus()).thenThrow(new ConnectionException("refused"));

        mockMvc.perform(get("/api/v1/scheduler/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title", is("Scheduler Connection Error")))
                .andExpect(jsonPath("$.type", containsString("https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // --- GET /api/v1/scheduler/nodes ---

    @Test
    void getClusterNodes_returnsNodes() throws Exception {
        List<ClusterNode> nodes = List.of(
                new ClusterNode("node-1", Instant.now(), 10000L),
                new ClusterNode("node-2", Instant.now(), 10000L)
        );
        when(schedulerReader.getClusterNodes()).thenReturn(nodes);

        mockMvc.perform(get("/api/v1/scheduler/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].instanceId", is("node-1")))
                .andExpect(jsonPath("$.data[1].instanceId", is("node-2")));
    }

    @Test
    void getClusterNodes_noNodes_returnsEmptyList() throws Exception {
        when(schedulerReader.getClusterNodes()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scheduler/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getClusterNodes_connectionError_returns503() throws Exception {
        when(schedulerReader.getClusterNodes()).thenThrow(new ConnectionException("timeout"));

        mockMvc.perform(get("/api/v1/scheduler/nodes"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title", is("Scheduler Connection Error")));
    }
}
