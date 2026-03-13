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

import com.vlastolabs.quartzdash.exception.ConnectionException;
import com.vlastolabs.quartzdash.exception.JobNotFoundException;
import com.vlastolabs.quartzdash.exception.QuartzDashException;
import com.vlastolabs.quartzdash.domain.JobKey;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link GlobalExceptionHandler} covering all handled exception types.
 * Uses JobController as a convenient test harness.
 */
@WebMvcTest({JobController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SchedulerReader schedulerReader;

    @Test
    void jobNotFoundException_returns404WithProblemDetail() throws Exception {
        when(schedulerReader.getJobByKey(any())).thenThrow(new JobNotFoundException(new JobKey("DEFAULT", "missing")));

        mockMvc.perform(get("/api/v1/jobs/DEFAULT/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Job Not Found")))
                .andExpect(jsonPath("$.type", containsString( "https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void connectionException_returns503WithProblemDetail() throws Exception {
        when(schedulerReader.getAllJobs()).thenThrow(new ConnectionException("connection refused"));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title", is("Scheduler Connection Error")))
                .andExpect(jsonPath("$.type", containsString("https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.detail", containsString("connection refused")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void quartzDashException_returns500WithProblemDetail() throws Exception {
        when(schedulerReader.getAllJobs()).thenThrow(new QuartzDashException("scheduler exploded"));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title", is("QuartzDash Error")))
                .andExpect(jsonPath("$.type", containsString( "https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.detail", is("scheduler exploded")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void illegalArgumentException_returns400WithProblemDetail() throws Exception {
        when(schedulerReader.getAllJobs()).thenThrow(new IllegalArgumentException("bad input"));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")))
                .andExpect(jsonPath("$.type", containsString( "https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.detail", is("bad input")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void genericException_returns500WithoutLeakingDetails() throws Exception {
        when(schedulerReader.getAllJobs()).thenThrow(new RuntimeException("secret internal state"));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title", is("Internal Server Error")))
                .andExpect(jsonPath("$.type", containsString( "https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.detail", not(containsString("secret internal state"))))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void allProblemDetails_containTimestamp() throws Exception {
        when(schedulerReader.getAllJobs()).thenThrow(new ConnectionException("down"));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
