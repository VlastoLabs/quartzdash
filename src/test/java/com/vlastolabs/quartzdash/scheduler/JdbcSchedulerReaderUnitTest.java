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

import com.vlastolabs.quartzdash.config.QuartzDashProperties;
import com.vlastolabs.quartzdash.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcSchedulerReaderUnitTest {

    private static final String TABLE_PREFIX = "QRTZ_";

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private QuartzDashProperties properties;
    @Mock private QuartzDashProperties.ConnectionConfig connection;
    @Mock private QuartzDashProperties.JdbcConfig jdbc;
    @Mock private ResultSet rs;

    private JdbcSchedulerReader reader;

    @BeforeEach
    void setUp() {
        when(properties.connection()).thenReturn(connection);
        when(connection.jdbc()).thenReturn(jdbc);
        when(jdbc.tablePrefix()).thenReturn(TABLE_PREFIX);
        reader = new JdbcSchedulerReader(jdbcTemplate, properties);
    }

    // ── Shared ResultSet stubbing helpers ─────────────────────────────────────

    private void stubTriggerRow(String triggerName) throws SQLException {
        when(rs.getString("TRIGGER_NAME")).thenReturn(triggerName);
        when(rs.getString("TRIGGER_GROUP")).thenReturn("DEFAULT");
        when(rs.getString("TRIGGER_TYPE")).thenReturn("CRON");
        when(rs.getString("TRIGGER_STATE")).thenReturn("WAITING");
        when(rs.getString("CRON_EXPRESSION")).thenReturn("0 * * * * ?");
        when(rs.getLong("PREV_FIRE_TIME")).thenReturn(0L);
        when(rs.getLong("NEXT_FIRE_TIME")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);
    }

    private void stubJobRow(String group, String name) throws SQLException {
        when(rs.getString("JOB_GROUP")).thenReturn(group);
        when(rs.getString("JOB_NAME")).thenReturn(name);
        when(rs.getString("JOB_CLASS_NAME")).thenReturn("com.example." + name);
        when(rs.getString("DESCRIPTION")).thenReturn(null);
        when(rs.getObject("IS_DURABLE")).thenReturn(false);
        when(rs.getObject("REQUESTS_RECOVERY")).thenReturn(false);
    }

    /** Runs a ResultSetExtractor captured from a jdbcTemplate.query(sql, extractor, args...) call. */
    @SuppressWarnings("unchecked")
    private <T> T runExtractor() throws SQLException {
        ArgumentCaptor<ResultSetExtractor<T>> captor = ArgumentCaptor.forClass(ResultSetExtractor.class);
        verify(jdbcTemplate, atLeastOnce()).query(anyString(), captor.capture(), any());
        return captor.getValue().extractData(rs);
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("accepts valid alphanumeric prefix")
        void acceptsAlphanumericPrefix() {
            when(jdbc.tablePrefix()).thenReturn("QRTZ_");
            assertThatNoException().isThrownBy(
                    () -> new JdbcSchedulerReader(jdbcTemplate, properties));
        }

        @Test
        @DisplayName("accepts prefix with dots")
        void acceptsPrefixWithDots() {
            when(jdbc.tablePrefix()).thenReturn("schema.QRTZ_");
            assertThatNoException().isThrownBy(
                    () -> new JdbcSchedulerReader(jdbcTemplate, properties));
        }

        @Test
        @DisplayName("rejects empty prefix — SAFE_PREFIX uses + not *")
        void rejectsEmptyPrefix() {
            when(jdbc.tablePrefix()).thenReturn("");
            assertThatThrownBy(() -> new JdbcSchedulerReader(jdbcTemplate, properties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsafe table prefix");
        }

        @Test
        @DisplayName("rejects null prefix")
        void rejectsNullPrefix() {
            when(jdbc.tablePrefix()).thenReturn(null);
            assertThatThrownBy(() -> new JdbcSchedulerReader(jdbcTemplate, properties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsafe table prefix");
        }

        @ParameterizedTest(name = "rejects unsafe prefix [{0}]")
        @ValueSource(strings = {"'; DROP TABLE--", "qrtz/", "pre fix", "pre'fix", "pre\"fix"})
        @DisplayName("rejects SQL-unsafe prefixes")
        void rejectsUnsafePrefix(String unsafePrefix) {
            when(jdbc.tablePrefix()).thenReturn(unsafePrefix);
            assertThatThrownBy(() -> new JdbcSchedulerReader(jdbcTemplate, properties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsafe table prefix");
        }
    }

    // -------------------------------------------------------------------------
    // getSchedulerStatus
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getSchedulerStatus")
    class GetSchedulerStatusTests {

        @Test
        @DisplayName("returns NON_CLUSTERED instanceId when no cluster nodes exist")
        void nonClusteredWhenNoNodes() {
            when(jdbcTemplate.query(contains("SCHEDULER_STATE"), any(RowMapper.class)))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForObject(contains("JOB_COUNT"), any(RowMapper.class)))
                    .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1))
                            .mapRow(mockCountResultSet(5, 3, 1), 0));

            SchedulerStatus status = reader.getSchedulerStatus();

            assertThat(status.instanceId()).isEqualTo("NON_CLUSTERED");
            assertThat(status.totalJobs()).isEqualTo(5);
            assertThat(status.totalTriggers()).isEqualTo(3);
            assertThat(status.state()).isEqualTo(SchedulerState.STARTED);
        }

        @Test
        @DisplayName("uses first node instanceId when clustered")
        void usesFirstNodeInstanceIdWhenClustered() {
            ClusterNode node = new ClusterNode("node-1", Instant.now(), 5000L);
            when(jdbcTemplate.query(contains("SCHEDULER_STATE"), any(RowMapper.class)))
                    .thenReturn(List.of(node));
            when(jdbcTemplate.queryForObject(contains("JOB_COUNT"), any(RowMapper.class)))
                    .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1))
                            .mapRow(mockCountResultSet(0, 0, 0), 0));

            assertThat(reader.getSchedulerStatus().instanceId()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("issues exactly one count query (not three)")
        void issuesOneCountQuery() {
            when(jdbcTemplate.query(contains("SCHEDULER_STATE"), any(RowMapper.class)))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1))
                            .mapRow(mockCountResultSet(0, 0, 0), 0));

            reader.getSchedulerStatus();

            verify(jdbcTemplate, times(1)).queryForObject(anyString(), any(RowMapper.class));
        }

        @Test
        @DisplayName("count SQL contains all three aliases")
        void countSqlContainsAllAliases() {
            when(jdbcTemplate.query(contains("SCHEDULER_STATE"), any(RowMapper.class)))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1))
                            .mapRow(mockCountResultSet(0, 0, 0), 0));

            reader.getSchedulerStatus();

            verify(jdbcTemplate).queryForObject(
                    argThat(sql -> sql.contains("JOB_COUNT")
                            && sql.contains("TRIGGER_COUNT")
                            && sql.contains("FIRED_COUNT")),
                    any(RowMapper.class));
        }

        @Test
        @DisplayName("timestamp is close to now")
        void timestampIsRecent() {
            Instant before = Instant.now();
            when(jdbcTemplate.query(contains("SCHEDULER_STATE"), any(RowMapper.class)))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1))
                            .mapRow(mockCountResultSet(0, 0, 0), 0));

            SchedulerStatus status = reader.getSchedulerStatus();
            Instant after = Instant.now();

            assertThat(status.polledAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        /** Creates a mock ResultSet that returns fixed values for the three count columns. */
        private ResultSet mockCountResultSet(int jobs, int triggers, int fired) {
            try {
                ResultSet mock = Mockito.mock(ResultSet.class);
                when(mock.getInt("JOB_COUNT")).thenReturn(jobs);
                when(mock.getInt("TRIGGER_COUNT")).thenReturn(triggers);
                when(mock.getInt("FIRED_COUNT")).thenReturn(fired);
                return mock;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("getClusterNodes")
    class GetClusterNodesTests {

        @Test
        @DisplayName("maps instance name, last check-in, and interval correctly")
        void mapsClusterNodeFields() throws SQLException {
            when(rs.getString("INSTANCE_NAME")).thenReturn("node-1");
            when(rs.getLong("LAST_CHECKIN_TIME")).thenReturn(1_000_000L);
            when(rs.wasNull()).thenReturn(false);
            when(rs.getLong("CHECKIN_INTERVAL")).thenReturn(10_000L);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            List<ClusterNode> nodes = reader.getClusterNodes();

            assertThat(nodes).hasSize(1);
            assertThat(nodes.getFirst().instanceId()).isEqualTo("node-1");
            assertThat(nodes.getFirst().lastCheckin()).isEqualTo(Instant.ofEpochMilli(1_000_000L));
            assertThat(nodes.getFirst().checkinInterval()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("falls back to UNKNOWN when instance name is null")
        void fallsBackToUnknownWhenInstanceNameNull() throws SQLException {
            when(rs.getString("INSTANCE_NAME")).thenReturn(null);
            when(rs.getLong("LAST_CHECKIN_TIME")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(false);
            when(rs.getLong("CHECKIN_INTERVAL")).thenReturn(5000L);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            assertThat(reader.getClusterNodes().getFirst().instanceId()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("returns null lastCheckin when column is SQL NULL")
        void returnsNullLastCheckinWhenSqlNull() throws SQLException {
            when(rs.getString("INSTANCE_NAME")).thenReturn("node-1");
            when(rs.getLong("LAST_CHECKIN_TIME")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getLong("CHECKIN_INTERVAL")).thenReturn(5000L);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            assertThat(reader.getClusterNodes().getFirst().lastCheckin()).isNull();
        }

        @Test
        @DisplayName("uses correct table prefix in SQL")
        void usesCorrectTablePrefix() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

            reader.getClusterNodes();

            verify(jdbcTemplate).query(
                    contains(TABLE_PREFIX + "SCHEDULER_STATE"),
                    any(RowMapper.class));
        }
    }

    @Nested
    @DisplayName("Job queries")
    class JobQueryTests {

        @Test
        @DisplayName("getAllJobs uses no-varargs JdbcTemplate overload")
        void getAllJobsUsesNoVarargsOverload() {
            // The no-args path must call query(sql, extractor) — NOT query(sql, extractor, args[])
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenReturn(List.of());

            reader.getAllJobs();

            verify(jdbcTemplate).query(
                    contains(TABLE_PREFIX),
                    any(ResultSetExtractor.class));
            // Confirm the varargs overload was NOT called for getAllJobs
            verify(jdbcTemplate, never()).query(
                    anyString(), any(ResultSetExtractor.class), any(Object[].class));
        }

        @Test
        @DisplayName("getJobsByGroup uses varargs overload with group as bind variable")
        void getJobsByGroupPassesGroupAsBindVariable() {
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any()))
                    .thenReturn(List.of());

            reader.getJobsByGroup("myGroup");

            verify(jdbcTemplate).query(
                    anyString(),
                    any(ResultSetExtractor.class),
                    eq("myGroup"));
        }

        @Test
        @DisplayName("getJobsByGroup rejects null group")
        void getJobsByGroupRejectsNull() {
            assertThatThrownBy(() -> reader.getJobsByGroup(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("group");
        }

        @Test
        @DisplayName("returns empty list when ResultSet is empty")
        void returnsEmptyListForEmptyResultSet() throws SQLException {
            when(rs.next()).thenReturn(false);
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            assertThat(reader.getAllJobs()).isEmpty();
        }

        @Test
        @DisplayName("maps a job with no triggers correctly")
        void mapsJobWithNoTriggers() throws SQLException {
            when(rs.next()).thenReturn(true, false);
            stubJobRow("DEFAULT", "myJob");
            when(rs.getString("TRIGGER_NAME")).thenReturn(null);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            List<JobSummary> jobs = reader.getAllJobs();

            assertThat(jobs).hasSize(1);
            JobSummary job = jobs.getFirst();
            assertThat(job.key().name()).isEqualTo("myJob");
            assertThat(job.key().group()).isEqualTo("DEFAULT");
            assertThat(job.durable()).isFalse();
            assertThat(job.requestsRecovery()).isFalse();
            assertThat(job.triggers()).isEmpty();
        }

        @Test
        @DisplayName("maps a job with multiple triggers correctly")
        void mapsJobWithMultipleTriggers() throws SQLException {
            // Two rows: same job, different triggers
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("JOB_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("JOB_NAME")).thenReturn("myJob");
            when(rs.getString("JOB_CLASS_NAME")).thenReturn("com.example.MyJob");
            when(rs.getString("DESCRIPTION")).thenReturn(null);
            when(rs.getObject("IS_DURABLE")).thenReturn(false);
            when(rs.getObject("REQUESTS_RECOVERY")).thenReturn(false);
            when(rs.getString("TRIGGER_NAME")).thenReturn("t1", "t2");
            when(rs.getString("TRIGGER_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("TRIGGER_TYPE")).thenReturn("CRON");
            when(rs.getString("TRIGGER_STATE")).thenReturn("WAITING");
            when(rs.getString("CRON_EXPRESSION")).thenReturn("0 * * * * ?");
            when(rs.getLong("PREV_FIRE_TIME")).thenReturn(0L);
            when(rs.getLong("NEXT_FIRE_TIME")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            List<JobSummary> jobs = reader.getAllJobs();

            assertThat(jobs).hasSize(1);
            assertThat(jobs.getFirst().triggers()).hasSize(2);
        }

        @Test
        @DisplayName("two distinct jobs are returned as separate entries in insertion order")
        void twoDistinctJobsReturnedSeparately() throws SQLException {
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("JOB_GROUP")).thenReturn("DEFAULT", "OTHER");
            when(rs.getString("JOB_NAME")).thenReturn("jobA", "jobB");
            when(rs.getString("JOB_CLASS_NAME")).thenReturn("com.example.JobA", "com.example.JobB");
            when(rs.getString("DESCRIPTION")).thenReturn(null);
            when(rs.getObject("IS_DURABLE")).thenReturn(false);
            when(rs.getObject("REQUESTS_RECOVERY")).thenReturn(false);
            when(rs.getString("TRIGGER_NAME")).thenReturn(null);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            List<JobSummary> jobs = reader.getAllJobs();

            assertThat(jobs).hasSize(2);
            assertThat(jobs).extracting(j -> j.key().name()).containsExactly("jobA", "jobB");
        }

        @Test
        @DisplayName("trigger list on returned JobSummary is immutable")
        void triggerListIsImmutable() throws SQLException {
            when(rs.next()).thenReturn(true, false);
            stubJobRow("DEFAULT", "myJob");
            when(rs.getString("TRIGGER_NAME")).thenReturn(null);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            List<JobSummary> jobs = reader.getAllJobs();

            assertThatThrownBy(() -> jobs.getFirst().triggers().add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("SQLException from ResultSet propagates without raw RuntimeException wrapping")
        void doesNotWrapSqlExceptionInRawRuntimeException() throws SQLException {
            when(rs.next()).thenReturn(true);
            when(rs.getString("JOB_GROUP")).thenReturn("G");
            when(rs.getString("JOB_NAME")).thenReturn("J");
            when(rs.getString("JOB_CLASS_NAME")).thenThrow(new SQLException("column error"));

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            assertThatThrownBy(() -> reader.getAllJobs())
                    .isInstanceOf(SQLException.class)
                    .hasMessage("column error");
        }
    }

    @Nested
    @DisplayName("getJobByKey")
    class GetJobByKeyTests {

        private final JobKey key = new JobKey("DEFAULT", "myJob");

        @Test
        @DisplayName("uses JOIN query — no separate trigger fetch (no N+1)")
        void usesJoinQueryNotSeparateTriggerFetch() {
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(), any()))
                    .thenReturn(List.of());

            reader.getJobByKey(key);

            verify(jdbcTemplate, times(1))
                    .query(anyString(), any(ResultSetExtractor.class), any(), any());
            verifyNoMoreInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("returns populated Optional when job exists")
        void returnsJobWhenFound() throws SQLException {
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("JOB_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("JOB_NAME")).thenReturn("myJob");
            when(rs.getString("JOB_CLASS_NAME")).thenReturn("com.example.MyJob");
            when(rs.getString("DESCRIPTION")).thenReturn("desc");
            when(rs.getObject("IS_DURABLE")).thenReturn(true);
            when(rs.getObject("REQUESTS_RECOVERY")).thenReturn(false);
            when(rs.getString("TRIGGER_NAME")).thenReturn(null);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(), any()))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            Optional<JobSummary> result = reader.getJobByKey(key);

            assertThat(result).isPresent();
            assertThat(result.get().key()).isEqualTo(key);
            assertThat(result.get().durable()).isTrue();
        }

        @Test
        @DisplayName("returns empty Optional when job does not exist")
        void returnsEmptyWhenNotFound() {
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(), any()))
                    .thenReturn(List.of());

            assertThat(reader.getJobByKey(key)).isEmpty();
        }

        @Test
        @DisplayName("passes job name and group as bind variables in correct order")
        void passesBindVariablesInCorrectOrder() {
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(), any()))
                    .thenReturn(List.of());

            reader.getJobByKey(key);

            verify(jdbcTemplate).query(
                    anyString(),
                    any(ResultSetExtractor.class),
                    eq(key.name()), eq(key.group()));
        }

        @Test
        @DisplayName("rejects null jobKey")
        void rejectsNullJobKey() {
            assertThatThrownBy(() -> reader.getJobByKey(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("jobKey");
        }

        @Test
        @DisplayName("SQL targets JOB_WITH_TRIGGERS_BY_KEY fragment")
        void sqlTargetsCorrectFragment() {
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(), any()))
                    .thenReturn(List.of());

            reader.getJobByKey(key);

            verify(jdbcTemplate).query(
                    argThat(sql -> sql.contains(TABLE_PREFIX + "JOB_DETAILS")
                            && sql.contains(TABLE_PREFIX + "TRIGGERS")),
                    any(ResultSetExtractor.class),
                    any(), any());
        }
    }

    @Nested
    @DisplayName("Trigger queries")
    class TriggerQueryTests {

        @Test
        @DisplayName("getTriggersByGroup adds TRIGGER_GROUP WHERE clause")
        void getTriggersByGroupAddsWhereClause() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            reader.getTriggersByGroup("myGroup");

            verify(jdbcTemplate).query(
                    argThat(sql -> sql.contains("TRIGGER_GROUP = ?")),
                    any(RowMapper.class),
                    eq("myGroup"));
        }

        @Test
        @DisplayName("getTriggersByState adds TRIGGER_STATE WHERE clause with enum name")
        void getTriggersByStateAddsWhereClause() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            reader.getTriggersByState(TriggerState.PAUSED);

            verify(jdbcTemplate).query(
                    argThat(sql -> sql.contains("TRIGGER_STATE = ?")),
                    any(RowMapper.class),
                    eq("PAUSED"));
        }

        @Test
        @DisplayName("getTriggersByGroupAndState adds both WHERE conditions")
        void getTriggersByGroupAndStateAddsBothConditions() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            reader.getTriggersByGroupAndState("myGroup", TriggerState.WAITING);

            verify(jdbcTemplate).query(
                    argThat(sql -> sql.contains("TRIGGER_GROUP = ?") && sql.contains("TRIGGER_STATE = ?")),
                    any(RowMapper.class),
                    eq("myGroup"), eq("WAITING"));
        }

        @Test
        @DisplayName("getTriggersByGroup rejects null group")
        void nullGroupRejectedInGetTriggersByGroup() {
            assertThatThrownBy(() -> reader.getTriggersByGroup(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("group");
        }

        @Test
        @DisplayName("getTriggersByGroupAndState rejects null group")
        void nullGroupRejectedInGetTriggersByGroupAndState() {
            assertThatThrownBy(() -> reader.getTriggersByGroupAndState(null, TriggerState.WAITING))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("group");
        }

        @Test
        @DisplayName("getTriggersByState rejects null state")
        void nullStateIsRejected() {
            assertThatThrownBy(() -> reader.getTriggersByState(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("state");
        }

        @Test
        @DisplayName("getTriggersByGroupAndState rejects null state")
        void nullStateRejectedInGetTriggersByGroupAndState() {
            assertThatThrownBy(() -> reader.getTriggersByGroupAndState("g", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("state");
        }

        @Test
        @DisplayName("mapTriggerSummary maps all CRON trigger fields correctly")
        void mapsCronTriggerCorrectly() throws SQLException {
            when(rs.getString("TRIGGER_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("TRIGGER_NAME")).thenReturn("cronTrigger");
            when(rs.getString("TRIGGER_TYPE")).thenReturn("CRON");
            when(rs.getString("TRIGGER_STATE")).thenReturn("WAITING");
            when(rs.getString("CRON_EXPRESSION")).thenReturn("0 0 * * * ?");
            when(rs.getLong("PREV_FIRE_TIME")).thenReturn(1_000_000L);
            when(rs.getLong("NEXT_FIRE_TIME")).thenReturn(2_000_000L);
            when(rs.wasNull()).thenReturn(false);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(1);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            TriggerSummary t = reader.getAllTriggers().getFirst();

            assertThat(t.key().name()).isEqualTo("cronTrigger");
            assertThat(t.key().group()).isEqualTo("DEFAULT");
            assertThat(t.type()).isEqualTo(TriggerType.CRON);
            assertThat(t.state()).isEqualTo(TriggerState.WAITING);
            assertThat(t.cronExpression()).isEqualTo("0 0 * * * ?");
            assertThat(t.previousFireTime()).isEqualTo(Instant.ofEpochMilli(1_000_000L));
            assertThat(t.nextFireTime()).isEqualTo(Instant.ofEpochMilli(2_000_000L));
            assertThat(t.misfireInstruction()).isEqualTo(1);
        }

        @ParameterizedTest(name = "trigger type [{0}] → [{1}]")
        @CsvSource({
                "CRON,      CRON",
                "SIMPLE,    SIMPLE",
                "CAL_INT,   CALENDAR_INTERVAL",
                "DAILY_INT, DAILY_TIME_INTERVAL",
                "BLOB,      CUSTOM",
                "UNKNOWN,   CUSTOM"   // unknown falls back to CUSTOM
        })
        @DisplayName("maps all known trigger types including unknown fallback")
        void mapsAllTriggerTypes(String raw, String expected) throws SQLException {
            when(rs.getString("TRIGGER_TYPE")).thenReturn(raw.trim());
            when(rs.getString("TRIGGER_STATE")).thenReturn("NONE");
            when(rs.getString("TRIGGER_GROUP")).thenReturn("G");
            when(rs.getString("TRIGGER_NAME")).thenReturn("T");
            when(rs.getString("CRON_EXPRESSION")).thenReturn(null);
            when(rs.getLong(anyString())).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            assertThat(reader.getAllTriggers().getFirst().type())
                    .isEqualTo(TriggerType.valueOf(expected.trim()));
        }

        @ParameterizedTest(name = "trigger state [{0}] → [{1}]")
        @CsvSource({
                "WAITING,         WAITING",
                "ACQUIRED,        WAITING",   // ACQUIRED maps to WAITING
                "EXECUTING,       BLOCKED",
                "COMPLETE,        COMPLETE",
                "PAUSED,          PAUSED",
                "PAUSED_BLOCKED,  BLOCKED",   // PAUSED_BLOCKED maps to BLOCKED
                "ERROR,           ERROR",
                "NONE,            NONE",
                "BOGUS,           NONE"        // unknown falls back to NONE
        })
        @DisplayName("maps all known trigger states including unknown fallback")
        void mapsAllTriggerStates(String raw, String expected) throws SQLException {
            when(rs.getString("TRIGGER_STATE")).thenReturn(raw.trim());
            when(rs.getString("TRIGGER_TYPE")).thenReturn("SIMPLE");
            when(rs.getString("TRIGGER_GROUP")).thenReturn("G");
            when(rs.getString("TRIGGER_NAME")).thenReturn("T");
            when(rs.getString("CRON_EXPRESSION")).thenReturn(null);
            when(rs.getLong(anyString())).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            assertThat(reader.getAllTriggers().getFirst().state())
                    .isEqualTo(TriggerState.valueOf(expected.trim()));
        }

        @Test
        @DisplayName("null PREV_FIRE_TIME and NEXT_FIRE_TIME produce null Instants")
        void nullFireTimesProduceNullInstants() throws SQLException {
            when(rs.getString("TRIGGER_GROUP")).thenReturn("G");
            when(rs.getString("TRIGGER_NAME")).thenReturn("T");
            when(rs.getString("TRIGGER_TYPE")).thenReturn("SIMPLE");
            when(rs.getString("TRIGGER_STATE")).thenReturn("NONE");
            when(rs.getString("CRON_EXPRESSION")).thenReturn(null);
            when(rs.getLong("PREV_FIRE_TIME")).thenReturn(0L);
            when(rs.getLong("NEXT_FIRE_TIME")).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            TriggerSummary trigger = reader.getAllTriggers().getFirst();

            assertThat(trigger.previousFireTime()).isNull();
            assertThat(trigger.nextFireTime()).isNull();
        }
    }

    @Nested
    @DisplayName("getTriggerByKey")
    class GetTriggerByKeyTests {

        private final TriggerKey key = new TriggerKey("DEFAULT", "myTrigger");

        @Test
        @DisplayName("returns populated Optional when trigger is found")
        void returnsWhenFound() throws SQLException {
            when(rs.getString("TRIGGER_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("TRIGGER_NAME")).thenReturn("myTrigger");
            when(rs.getString("TRIGGER_TYPE")).thenReturn("SIMPLE");
            when(rs.getString("TRIGGER_STATE")).thenReturn("WAITING");
            when(rs.getString("CRON_EXPRESSION")).thenReturn(null);
            when(rs.getLong(anyString())).thenReturn(0L);
            when(rs.wasNull()).thenReturn(true);
            when(rs.getInt("MISFIRE_INSTR")).thenReturn(0);

            // getTriggerByKey now uses jdbcTemplate.query (not queryForObject)
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            Optional<TriggerSummary> result = reader.getTriggerByKey(key);

            assertThat(result).isPresent();
            assertThat(result.get().key()).isEqualTo(key);
        }

        @Test
        @DisplayName("returns empty Optional when trigger is not found")
        void returnsEmptyWhenNotFound() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                    .thenReturn(List.of());

            assertThat(reader.getTriggerByKey(key)).isEmpty();
        }

        @Test
        @DisplayName("passes trigger name and group as bind variables in correct order")
        void passesBindVariablesInCorrectOrder() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                    .thenReturn(List.of());

            reader.getTriggerByKey(key);

            verify(jdbcTemplate).query(
                    anyString(),
                    any(RowMapper.class),
                    eq(key.name()), eq(key.group()));
        }

        @Test
        @DisplayName("rejects null triggerKey")
        void rejectsNullTriggerKey() {
            assertThatThrownBy(() -> reader.getTriggerByKey(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("triggerKey");
        }
    }

    @Nested
    @DisplayName("getFiredTriggers")
    class GetFiredTriggersTests {

        @Test
        @DisplayName("maps all FiredTriggerEntry fields correctly")
        void mapsAllFields() throws SQLException {
            when(rs.getString("ENTRY_ID")).thenReturn("entry-1");
            when(rs.getString("SCHED_NAME")).thenReturn("QuartzScheduler");
            when(rs.getString("JOB_NAME")).thenReturn("myJob");
            when(rs.getString("JOB_GROUP")).thenReturn("DEFAULT");
            when(rs.getString("TRIGGER_NAME")).thenReturn("myTrigger");
            when(rs.getString("TRIGGER_GROUP")).thenReturn("DEFAULT");
            when(rs.getLong("FIRED_TIME")).thenReturn(1_000_000L);
            when(rs.getLong("SCHED_TIME")).thenReturn(2_000_000L);
            when(rs.wasNull()).thenReturn(false);
            when(rs.getInt("PRIORITY")).thenReturn(5);
            when(rs.getString("STATE")).thenReturn("EXECUTING");
            when(rs.getString("INSTANCE_NAME")).thenReturn("node-1");

            when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            FiredTriggerEntry e = reader.getFiredTriggers().getFirst();

            assertThat(e.entryId()).isEqualTo("entry-1");
            assertThat(e.schedulerName()).isEqualTo("QuartzScheduler");
            assertThat(e.jobName()).isEqualTo("myJob");
            assertThat(e.jobGroup()).isEqualTo("DEFAULT");
            assertThat(e.triggerName()).isEqualTo("myTrigger");
            assertThat(e.triggerGroup()).isEqualTo("DEFAULT");
            assertThat(e.firedAt()).isEqualTo(Instant.ofEpochMilli(1_000_000L));
            assertThat(e.scheduledFireTime()).isEqualTo(Instant.ofEpochMilli(2_000_000L));
            assertThat(e.priority()).isEqualTo(5);
            assertThat(e.state()).isEqualTo("EXECUTING");
            assertThat(e.instanceName()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("returns empty list when no fired triggers exist")
        void returnsEmptyListWhenNone() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

            assertThat(reader.getFiredTriggers()).isEmpty();
        }

        @Test
        @DisplayName("SQL targets FIRED_TRIGGERS table with correct prefix")
        void usesFiredTriggersTable() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

            reader.getFiredTriggers();

            verify(jdbcTemplate).query(
                    contains(TABLE_PREFIX + "FIRED_TRIGGERS"),
                    any(RowMapper.class));
        }
    }

    @Nested
    @DisplayName("resolveBoolean")
    class ResolveBooleanTests {

        @ParameterizedTest(name = "IS_DURABLE db value [{0}] → [{1}]")
        @MethodSource("booleanVariants")
        @DisplayName("resolves boolean-like JDBC values across DB dialects")
        void resolvesBooleanVariants(Object dbValue, boolean expectedDurable) throws SQLException {
            when(rs.next()).thenReturn(true, false);
            stubJobRow("G", "J");
            when(rs.getObject("IS_DURABLE")).thenReturn(dbValue);
            when(rs.getString("TRIGGER_NAME")).thenReturn(null);

            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                    .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(1)).extractData(rs));

            assertThat(reader.getAllJobs().getFirst().durable()).isEqualTo(expectedDurable);
        }

        static List<Arguments> booleanVariants() {
            return List.of(
                    Arguments.of(true,    true),    // native BOOLEAN (PostgreSQL)
                    Arguments.of(false,   false),
                    Arguments.of(1,       true),    // INTEGER 1 (MySQL, Oracle)
                    Arguments.of(0,       false),
                    Arguments.of("Y",     true),    // char (some Oracle setups)
                    Arguments.of("N",     false),
                    Arguments.of("true",  true),    // string
                    Arguments.of("false", false),
                    Arguments.of("1",     true),
                    Arguments.of("0",     false),
                    Arguments.of(null,    false)    // SQL NULL → false
            );
        }
    }
}