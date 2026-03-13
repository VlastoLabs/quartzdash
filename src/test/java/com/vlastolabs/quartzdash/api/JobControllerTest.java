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

import com.vlastolabs.quartzdash.domain.JobKey;
import com.vlastolabs.quartzdash.domain.JobSummary;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({JobController.class, GlobalExceptionHandler.class})
class JobControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SchedulerReader schedulerReader;

    @Test
    void getJobs_returnsPagedResults() throws Exception {
        List<JobSummary> jobs = List.of(jobSummary("DEFAULT", "job1"), jobSummary("DEFAULT", "job2"));
        when(schedulerReader.getAllJobs()).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getJobs_withGroupFilter_callsGetByGroup() throws Exception {
        List<JobSummary> jobs = List.of(jobSummary("payments", "charge"));
        when(schedulerReader.getJobsByGroup("payments")).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs").param("group", "payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].key.name", is("charge")));
    }

    @Test
    void getJobs_paginationWorks() throws Exception {
        List<JobSummary> jobs = IntStream.range(0, 25)
                .mapToObj(i -> jobSummary("DEFAULT", "job" + i))
                .toList();
        when(schedulerReader.getAllJobs()).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(10)))
                .andExpect(jsonPath("$.data.totalElements", is(25)))
                .andExpect(jsonPath("$.data.number", is(1)));
    }

    @Test
    void getJobs_pageOutOfBounds_returnsEmptyContent() throws Exception {
        when(schedulerReader.getAllJobs()).thenReturn(List.of(jobSummary("DEFAULT", "job1")));

        mockMvc.perform(get("/api/v1/jobs").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    void getJobs_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")));
    }

    @Test
    void getJobs_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")));
    }

    @Test
    void getJobs_sizeExceedsMax_isCappedAt100() throws Exception {
        List<JobSummary> jobs = IntStream.range(0, 50)
                .mapToObj(i -> jobSummary("DEFAULT", "job" + i))
                .toList();
        when(schedulerReader.getAllJobs()).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", lessThanOrEqualTo(100)));
    }

    @Test
    void getJobs_emptyResult_returnsEmptyPage() throws Exception {
        when(schedulerReader.getAllJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)));
    }

    // --- GET /api/v1/jobs/{group}/{name} ---

    @Test
    void getJob_found_returnsJob() throws Exception {
        JobSummary job = jobSummary("DEFAULT", "myJob");
        when(schedulerReader.getJobByKey(new JobKey("DEFAULT", "myJob"))).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/jobs/DEFAULT/myJob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key.group", is("DEFAULT")))
                .andExpect(jsonPath("$.data.key.name", is("myJob")));
    }

    @Test
    void getJob_notFound_returns404() throws Exception {
        when(schedulerReader.getJobByKey(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/DEFAULT/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Job Not Found")))
                .andExpect(jsonPath("$.type", containsString("https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // --- helpers ---

    private JobSummary jobSummary(String group, String name) {
        return new JobSummary(new JobKey(group, name), "com.example.MyJob",
                "Test job", false, false, List.of());
    }
}
