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

import com.vlastolabs.quartzdash.domain.TriggerKey;
import com.vlastolabs.quartzdash.domain.TriggerState;
import com.vlastolabs.quartzdash.domain.TriggerSummary;
import com.vlastolabs.quartzdash.exception.TriggerNotFoundException;
import com.vlastolabs.quartzdash.scheduler.SchedulerReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for trigger-level operations.
 */
@RestController
@RequestMapping("/api/v1/triggers")
public class TriggerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SchedulerReader schedulerReader;

    public TriggerController(SchedulerReader schedulerReader) {
        this.schedulerReader = schedulerReader;
    }

    /**
     * Gets all triggers with optional group and state filters.
     *
     * @param group optional group name to filter by
     * @param state optional {@link TriggerState} name to filter by
     * @param page  zero-based page index (default 0)
     * @param size  page size, capped at {@value MAX_PAGE_SIZE} (default 20)
     * @return paginated list of triggers wrapped in {@link ApiResponse}
     * @throws IllegalArgumentException if {@code state} is not a valid {@link TriggerState}
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TriggerSummary>>> getTriggers(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePaginationParams(page, size);
        TriggerState triggerState = parseTriggerState(state);

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize);

        List<TriggerSummary> allTriggers = fetchTriggers(group, triggerState);
        Page<TriggerSummary> triggerPage = toPage(allTriggers, pageable);

        return ResponseEntity.ok(ApiResponse.success(triggerPage));
    }

    /**
     * Gets a specific trigger by its group and name.
     *
     * @param group the trigger group
     * @param name  the trigger name
     * @return the trigger wrapped in {@link ApiResponse}
     * @throws TriggerNotFoundException if no trigger with the given key exists
     */
    @GetMapping("/{group}/{name}")
    public ResponseEntity<ApiResponse<TriggerSummary>> getTrigger(
            @PathVariable String group,
            @PathVariable String name) {

        TriggerKey triggerKey = new TriggerKey(group, name);

        return schedulerReader.getTriggerByKey(triggerKey)
                .map(trigger -> ResponseEntity.ok(ApiResponse.success(trigger)))
                .orElseThrow(() -> new TriggerNotFoundException(triggerKey));
    }

    private List<TriggerSummary> fetchTriggers(String group, TriggerState state) {
        boolean hasGroup = group != null && !group.isBlank();
        boolean hasState = state != null;

        if (hasGroup && hasState) {
            return schedulerReader.getTriggersByGroupAndState(group, state);
        } else if (hasGroup) {
            return schedulerReader.getTriggersByGroup(group);
        } else if (hasState) {
            return schedulerReader.getTriggersByState(state);
        } else {
            return schedulerReader.getAllTriggers();
        }
    }

    private TriggerState parseTriggerState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            return TriggerState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid trigger state: '%s'. Valid values are: %s"
                    .formatted(state, List.of(TriggerState.values())));
        }
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