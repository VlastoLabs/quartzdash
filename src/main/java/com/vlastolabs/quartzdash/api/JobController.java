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
import com.vlastolabs.quartzdash.exception.JobNotFoundException;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for job-level operations.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SchedulerReader schedulerReader;

    public JobController(SchedulerReader schedulerReader) {
        this.schedulerReader = schedulerReader;
    }

    /**
     * Gets all jobs with optional group filter, supporting pagination and sorting.
     *
     * @param group optional group name to filter by
     * @param page  zero-based page index (default 0)
     * @param size  page size, capped at {@value MAX_PAGE_SIZE} (default 20)
     * @param sort  comma-separated sort fields (default "key.name")
     * @return paginated list of jobs wrapped in {@link ApiResponse}
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobSummary>>> getJobs(
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "key.name") String sort) {

        validatePaginationParams(page, size);

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(sort.split(",")));

        List<JobSummary> allJobs = (group != null && !group.isBlank())
                ? schedulerReader.getJobsByGroup(group)
                : schedulerReader.getAllJobs();

        Page<JobSummary> jobPage = toPage(allJobs, pageable);
        return ResponseEntity.ok(ApiResponse.success(jobPage));
    }

    /**
     * Gets a specific job by its group and name.
     *
     * @param group the job group
     * @param name  the job name
     * @return the job wrapped in {@link ApiResponse}
     * @throws JobNotFoundException if no job with the given key exists
     */
    @GetMapping("/{group}/{name}")
    public ResponseEntity<ApiResponse<JobSummary>> getJob(
            @PathVariable String group,
            @PathVariable String name) {

        JobKey jobKey = new JobKey(group, name);

        return schedulerReader.getJobByKey(jobKey)
                .map(job -> ResponseEntity.ok(ApiResponse.success(job)))
                .orElseThrow(() -> new JobNotFoundException(jobKey));
    }

    private void validatePaginationParams(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }
    }

    private <T> Page<T> toPage(List<T> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}