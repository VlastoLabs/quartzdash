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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TriggerController.class, GlobalExceptionHandler.class})
class TriggerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SchedulerReader schedulerReader;

    // --- GET /api/v1/triggers ---

    @Test
    void getTriggers_noFilters_returnsAllTriggers() throws Exception {
        when(schedulerReader.getAllTriggers()).thenReturn(List.of(
                triggerSummary("DEFAULT", "t1"),
                triggerSummary("DEFAULT", "t2")
        ));

        mockMvc.perform(get("/api/v1/triggers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    void getTriggers_withGroupFilter_delegatesToGetByGroup() throws Exception {
        when(schedulerReader.getTriggersByGroup("payments")).thenReturn(
                List.of(triggerSummary("payments", "chargeEveryHour"))
        );

        mockMvc.perform(get("/api/v1/triggers").param("group", "payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].key.group", is("payments")));

        verify(schedulerReader).getTriggersByGroup("payments");
        verify(schedulerReader, never()).getAllTriggers();
    }

    @Test
    void getTriggers_withStateFilter_delegatesToGetByState() throws Exception {
        when(schedulerReader.getTriggersByState(TriggerState.PAUSED)).thenReturn(
                List.of(triggerSummary("DEFAULT", "paused1"))
        );

        mockMvc.perform(get("/api/v1/triggers").param("state", "PAUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        verify(schedulerReader).getTriggersByState(TriggerState.PAUSED);
    }

    @Test
    void getTriggers_withGroupAndStateFilter_delegatesToGetByGroupAndState() throws Exception {
        when(schedulerReader.getTriggersByGroupAndState("payments", TriggerState.WAITING)).thenReturn(
                List.of(triggerSummary("payments", "t1"))
        );

        mockMvc.perform(get("/api/v1/triggers").param("group", "payments").param("state", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        verify(schedulerReader).getTriggersByGroupAndState("payments", TriggerState.WAITING);
    }

    @Test
    void getTriggers_stateIsCaseInsensitive() throws Exception {
        when(schedulerReader.getTriggersByState(TriggerState.PAUSED)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/triggers").param("state", "paused"))
                .andExpect(status().isOk());

        verify(schedulerReader).getTriggersByState(TriggerState.PAUSED);
    }

    @Test
    void getTriggers_invalidState_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/triggers").param("state", "FLYING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")))
                .andExpect(jsonPath("$.detail", containsString("FLYING")));
    }

    @Test
    void getTriggers_paginationWorks() throws Exception {
        List<TriggerSummary> triggers = IntStream.range(0, 15)
                .mapToObj(i -> triggerSummary("DEFAULT", "t" + i))
                .toList();
        when(schedulerReader.getAllTriggers()).thenReturn(triggers);

        mockMvc.perform(get("/api/v1/triggers").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.totalElements", is(15)))
                .andExpect(jsonPath("$.data.number", is(1)));
    }

    @Test
    void getTriggers_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/triggers").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")));
    }

    @Test
    void getTriggers_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/triggers").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid Request")));
    }

    @Test
    void getTriggers_blankGroupIsIgnored_returnsAll() throws Exception {
        when(schedulerReader.getAllTriggers()).thenReturn(List.of(triggerSummary("DEFAULT", "t1")));

        mockMvc.perform(get("/api/v1/triggers").param("group", "   "))
                .andExpect(status().isOk());

        verify(schedulerReader).getAllTriggers();
        verify(schedulerReader, never()).getTriggersByGroup(any());
    }

    // --- GET /api/v1/triggers/{group}/{name} ---

    @Test
    void getTrigger_found_returnsTrigger() throws Exception {
        TriggerSummary trigger = triggerSummary("DEFAULT", "myTrigger");
        when(schedulerReader.getTriggerByKey(new TriggerKey("DEFAULT", "myTrigger")))
                .thenReturn(Optional.of(trigger));

        mockMvc.perform(get("/api/v1/triggers/DEFAULT/myTrigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key.group", is("DEFAULT")))
                .andExpect(jsonPath("$.data.key.name", is("myTrigger")));
    }

    @Test
    void getTrigger_notFound_returns404() throws Exception {
        when(schedulerReader.getTriggerByKey(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/triggers/DEFAULT/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Trigger Not Found")))
                .andExpect(jsonPath("$.type", containsString("https://www.rfc-editor.org/rfc/rfc7807")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private TriggerSummary triggerSummary(String group, String name) {
        return new TriggerSummary(new TriggerKey(group, name), TriggerState.WAITING,
                com.vlastolabs.quartzdash.domain.TriggerType.SIMPLE, null, null, null, 0);
    }
}
