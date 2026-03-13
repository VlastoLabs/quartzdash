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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * JDBC-backed implementation of {@link SchedulerReader} that queries Quartz scheduler tables directly.
 *
 * <p>All SQL is parameterised to prevent injection. The table prefix is validated at construction
 * time because it is interpolated into SQL identifiers rather than passed as a bind variable.
 */
@Component
public class JdbcSchedulerReader implements SchedulerReader {

    private static final Logger log = LoggerFactory.getLogger(JdbcSchedulerReader.class);

    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_.]+");

    private static final Map<String, TriggerType> TRIGGER_TYPE_MAP = Map.ofEntries(
            Map.entry("CRON",         TriggerType.CRON),
            Map.entry("SIMPLE",       TriggerType.SIMPLE),
            Map.entry("CAL_INT",      TriggerType.CALENDAR_INTERVAL),
            Map.entry("DAILY_INT",    TriggerType.DAILY_TIME_INTERVAL),
            Map.entry("BLOB",         TriggerType.CUSTOM)
    );

    private static final Map<String, TriggerState> TRIGGER_STATE_MAP = Map.ofEntries(
            Map.entry("WAITING",         TriggerState.WAITING),
            Map.entry("ACQUIRED",        TriggerState.WAITING),
            Map.entry("EXECUTING",       TriggerState.BLOCKED),
            Map.entry("COMPLETE",        TriggerState.COMPLETE),
            Map.entry("PAUSED",          TriggerState.PAUSED),
            Map.entry("PAUSED_BLOCKED",  TriggerState.BLOCKED),
            Map.entry("ERROR",           TriggerState.ERROR),
            Map.entry("NONE",            TriggerState.NONE)
    );

    private final JdbcTemplate jdbcTemplate;
    private final String tablePrefix;

    public JdbcSchedulerReader(
            @Qualifier("quartzJdbcTemplate") JdbcTemplate jdbcTemplate,
            QuartzDashProperties properties) {

        String prefix = properties.connection().jdbc().tablePrefix();
        if (prefix == null || !SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                    "Unsafe table prefix rejected to prevent SQL injection: [" + prefix + "]");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tablePrefix = prefix;
    }

    @Override
    public SchedulerStatus getSchedulerStatus() {
        List<ClusterNode> nodes = getClusterNodes();
        String instanceId = nodes.isEmpty() ? "NON_CLUSTERED" : nodes.getFirst().instanceId();

        String countSql = SqlFragments.withPrefix(SqlFragments.SELECT_COUNTS, tablePrefix);
        int[] counts = jdbcTemplate.queryForObject(countSql, (rs, rowNum) -> new int[]{
                rs.getInt("JOB_COUNT"),
                rs.getInt("TRIGGER_COUNT"),
                rs.getInt("FIRED_COUNT")
        });

        assert counts != null;

        return new SchedulerStatus(
                "QuartzScheduler",
                instanceId,
                SchedulerState.STARTED,
                counts[0],
                counts[1],
                counts[2],
                List.copyOf(nodes),
                Instant.now()
        );
    }

    @Override
    public List<ClusterNode> getClusterNodes() {
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_SCHEDULER_STATE, tablePrefix);
        return jdbcTemplate.query(sql, this::mapClusterNode);
    }

    @Override
    public List<JobSummary> getAllJobs() {
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_ALL_JOBS_WITH_TRIGGERS, tablePrefix);
        return jdbcTemplate.query(sql, this::extractJobsWithTriggers);
    }

    @Override
    public List<JobSummary> getJobsByGroup(String group) {
        Objects.requireNonNull(group, "group must not be null");
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_JOBS_WITH_TRIGGERS_BY_GROUP, tablePrefix);
        return jdbcTemplate.query(sql, this::extractJobsWithTriggers, group);
    }

    @Override
    public Optional<JobSummary> getJobByKey(JobKey jobKey) {
        Objects.requireNonNull(jobKey, "jobKey must not be null");
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_JOB_WITH_TRIGGERS_BY_KEY, tablePrefix);
        List<JobSummary> results = jdbcTemplate.query(
                sql, this::extractJobsWithTriggers, jobKey.name(), jobKey.group());

        if (results.isEmpty()) {
            log.debug("Job not found for key: {}/{}", jobKey.group(), jobKey.name());
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public List<TriggerSummary> getAllTriggers() {
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_TRIGGERS, tablePrefix);
        return jdbcTemplate.query(sql, this::mapTriggerSummary);
    }

    @Override
    public List<TriggerSummary> getTriggersByGroup(String group) {
        Objects.requireNonNull(group, "group must not be null");
        return queryTriggers(group, null);
    }

    @Override
    public List<TriggerSummary> getTriggersByState(TriggerState state) {
        Objects.requireNonNull(state, "state must not be null");
        return queryTriggers(null, state);
    }

    @Override
    public List<TriggerSummary> getTriggersByGroupAndState(String group, TriggerState state) {
        // Fix #5: null guard on public parameters
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(state, "state must not be null");
        return queryTriggers(group, state);
    }

    @Override
    public Optional<TriggerSummary> getTriggerByKey(TriggerKey triggerKey) {
        // Fix #5: null guard on public parameter
        Objects.requireNonNull(triggerKey, "triggerKey must not be null");
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_TRIGGER_BY_KEY, tablePrefix);
        List<TriggerSummary> results = jdbcTemplate.query(
                sql, this::mapTriggerSummary, triggerKey.name(), triggerKey.group());

        if (results.isEmpty()) {
            log.debug("Trigger not found for key: {}/{}", triggerKey.group(), triggerKey.name());
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    /**
     * Shared implementation for filtered trigger queries. Null means "no filter on that dimension".
     * Parameters are only ever supplied from the typed public overloads above, never from raw user input.
     */
    private List<TriggerSummary> queryTriggers(String group, TriggerState state) {
        String baseSql = SqlFragments.withPrefix(SqlFragments.SELECT_TRIGGERS, tablePrefix);

        List<String> conditions = new ArrayList<>();
        List<Object> args       = new ArrayList<>();

        if (group != null) {
            conditions.add("T.TRIGGER_GROUP = ?");
            args.add(group);
        }
        if (state != null) {
            conditions.add("T.TRIGGER_STATE = ?");
            args.add(state.name());
        }

        String sql = conditions.isEmpty()
                ? baseSql
                : baseSql + " WHERE " + String.join(" AND ", conditions);

        return jdbcTemplate.query(sql, this::mapTriggerSummary, args.toArray());
    }

    // -------------------------------------------------------------------------
    // Fired triggers
    // -------------------------------------------------------------------------

    @Override
    public List<FiredTriggerEntry> getFiredTriggers() {
        String sql = SqlFragments.withPrefix(SqlFragments.SELECT_FIRED_TRIGGERS, tablePrefix);
        return jdbcTemplate.query(sql, this::mapFiredTriggerEntry);
    }

    // -------------------------------------------------------------------------
    // Row mappers
    // -------------------------------------------------------------------------

    private ClusterNode mapClusterNode(ResultSet rs, int rowNum) throws SQLException {
        String instanceName    = rs.getString("INSTANCE_NAME");
        long lastCheckinMillis = rs.getLong("LAST_CHECKIN_TIME");
        Instant lastCheckin    = rs.wasNull() ? null : Instant.ofEpochMilli(lastCheckinMillis);
        long checkinInterval   = rs.getLong("CHECKIN_INTERVAL");

        return new ClusterNode(
                instanceName != null ? instanceName : "UNKNOWN",
                lastCheckin,
                checkinInterval
        );
    }

    private TriggerSummary mapTriggerSummary(ResultSet rs, int rowNum) throws SQLException {
        TriggerKey key = new TriggerKey(
                rs.getString("TRIGGER_GROUP"),
                rs.getString("TRIGGER_NAME")
        );
        TriggerType  type  = TRIGGER_TYPE_MAP.getOrDefault(rs.getString("TRIGGER_TYPE"),  TriggerType.CUSTOM);
        TriggerState state = TRIGGER_STATE_MAP.getOrDefault(rs.getString("TRIGGER_STATE"), TriggerState.NONE);

        return new TriggerSummary(
                key,
                state,
                type,
                rs.getString("CRON_EXPRESSION"),
                epochMillisToInstant(rs, "PREV_FIRE_TIME"),
                epochMillisToInstant(rs, "NEXT_FIRE_TIME"),
                rs.getInt("MISFIRE_INSTR")
        );
    }

    private FiredTriggerEntry mapFiredTriggerEntry(ResultSet rs, int rowNum) throws SQLException {
        return new FiredTriggerEntry(
                rs.getString("ENTRY_ID"),
                rs.getString("SCHED_NAME"),
                rs.getString("JOB_NAME"),
                rs.getString("JOB_GROUP"),
                rs.getString("TRIGGER_NAME"),
                rs.getString("TRIGGER_GROUP"),
                epochMillisToInstant(rs, "FIRED_TIME"),
                epochMillisToInstant(rs, "SCHED_TIME"),
                rs.getInt("PRIORITY"),
                rs.getString("STATE"),
                rs.getString("INSTANCE_NAME")
        );
    }

    /**
     * Collapses the flat JOIN result into a list of {@link JobSummary} objects, each carrying
     * its own immutable trigger list. A single pass over the ResultSet is sufficient because
     * rows are ordered by job group and name (guaranteed by the SQL ORDER BY clause).
     *
     * <p>Jobs with no triggers produce exactly one row with NULL trigger columns,
     * handled by the {@code TRIGGER_NAME} null-check below.
     */
    private List<JobSummary> extractJobsWithTriggers(ResultSet rs) throws SQLException {
        LinkedHashMap<JobKey, JobAccumulator> acc = new LinkedHashMap<>();

        while (rs.next()) {
            JobKey jobKey = new JobKey(rs.getString("JOB_GROUP"), rs.getString("JOB_NAME"));

            if (!acc.containsKey(jobKey)) {
                acc.put(jobKey, new JobAccumulator(
                        rs.getString("JOB_CLASS_NAME"),
                        rs.getString("DESCRIPTION"),
                        resolveBoolean(rs, "IS_DURABLE"),
                        resolveBoolean(rs, "REQUESTS_RECOVERY")
                ));
            }
            if (rs.getString("TRIGGER_NAME") != null) {
                acc.get(jobKey).triggers().add(mapTriggerSummary(rs, 0));
            }
        }

        return acc.entrySet().stream()
                .map(e -> {
                    JobAccumulator a = e.getValue();
                    return new JobSummary(
                            e.getKey(),
                            a.jobClassName(),
                            a.description(),
                            a.durable(),
                            a.requestsRecovery(),
                            List.copyOf(a.triggers())
                    );
                })
                .toList();
    }

    private static Instant epochMillisToInstant(ResultSet rs, String column) throws SQLException {
        long millis = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

    /**
     * Resolves a boolean-like JDBC value that may be stored as {@code BOOLEAN}, {@code INTEGER},
     * or a single-character string ({@code 'Y'/'N'} or {@code '1'/'0'}).
     *
     * <p>This handles cross-database portability quirks in Quartz schema dialects.
     */
    private static boolean resolveBoolean(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null)              return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n)  return n.intValue() != 0;
        String text = value.toString().trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "Y".equalsIgnoreCase(text);
    }

    private record JobAccumulator(
            String jobClassName,
            String description,
            boolean durable,
            boolean requestsRecovery,
            List<TriggerSummary> triggers
    ) {
        JobAccumulator(String jobClassName, String description, boolean durable, boolean requestsRecovery) {
            this(jobClassName, description, durable, requestsRecovery, new ArrayList<>());
        }
    }
}