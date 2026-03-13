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

/**
 * SQL query constants for Quartz table operations.
 */
final class SqlFragments {

    private SqlFragments() {
    }

    static final String TABLE_PREFIX_PLACEHOLDER = "{prefix}";

    /**
     * All jobs with their triggers in one query.
     * Jobs with no triggers produce one row with all T.* / C.* / S.* columns NULL.
     */
    static final String SELECT_ALL_JOBS_WITH_TRIGGERS = """
            SELECT J.JOB_NAME, J.JOB_GROUP, J.JOB_CLASS_NAME, J.DESCRIPTION,
                   J.IS_DURABLE, J.REQUESTS_RECOVERY,
                   T.TRIGGER_NAME, T.TRIGGER_GROUP, T.TRIGGER_STATE, T.TRIGGER_TYPE,
                   T.PREV_FIRE_TIME, T.NEXT_FIRE_TIME, T.MISFIRE_INSTR,
                   C.CRON_EXPRESSION,
                   S.REPEAT_INTERVAL, S.REPEAT_COUNT
            FROM {prefix}JOB_DETAILS J
            LEFT JOIN {prefix}TRIGGERS T
                   ON J.JOB_NAME = T.JOB_NAME AND J.JOB_GROUP = T.JOB_GROUP
            LEFT JOIN {prefix}CRON_TRIGGERS C
                   ON T.TRIGGER_NAME = C.TRIGGER_NAME AND T.TRIGGER_GROUP = C.TRIGGER_GROUP
            LEFT JOIN {prefix}SIMPLE_TRIGGERS S
                   ON T.TRIGGER_NAME = S.TRIGGER_NAME AND T.TRIGGER_GROUP = S.TRIGGER_GROUP
            ORDER BY J.JOB_GROUP, J.JOB_NAME
            """;

    /** Same as {@link #SELECT_ALL_JOBS_WITH_TRIGGERS} but scoped to a single job group. */
    static final String SELECT_JOBS_WITH_TRIGGERS_BY_GROUP = """
            SELECT J.JOB_NAME, J.JOB_GROUP, J.JOB_CLASS_NAME, J.DESCRIPTION,
                   J.IS_DURABLE, J.REQUESTS_RECOVERY,
                   T.TRIGGER_NAME, T.TRIGGER_GROUP, T.TRIGGER_STATE, T.TRIGGER_TYPE,
                   T.PREV_FIRE_TIME, T.NEXT_FIRE_TIME, T.MISFIRE_INSTR,
                   C.CRON_EXPRESSION,
                   S.REPEAT_INTERVAL, S.REPEAT_COUNT
            FROM {prefix}JOB_DETAILS J
            LEFT JOIN {prefix}TRIGGERS T
                   ON J.JOB_NAME = T.JOB_NAME AND J.JOB_GROUP = T.JOB_GROUP
            LEFT JOIN {prefix}CRON_TRIGGERS C
                   ON T.TRIGGER_NAME = C.TRIGGER_NAME AND T.TRIGGER_GROUP = C.TRIGGER_GROUP
            LEFT JOIN {prefix}SIMPLE_TRIGGERS S
                   ON T.TRIGGER_NAME = S.TRIGGER_NAME AND T.TRIGGER_GROUP = S.TRIGGER_GROUP
            WHERE J.JOB_GROUP = ?
            ORDER BY J.JOB_NAME
            """;

    /** Single job with all its triggers — eliminates the N+1 in {@code getJobByKey}. */
    static final String SELECT_JOB_WITH_TRIGGERS_BY_KEY = """
            SELECT J.JOB_NAME, J.JOB_GROUP, J.JOB_CLASS_NAME, J.DESCRIPTION,
                   J.IS_DURABLE, J.REQUESTS_RECOVERY,
                   T.TRIGGER_NAME, T.TRIGGER_GROUP, T.TRIGGER_STATE, T.TRIGGER_TYPE,
                   T.PREV_FIRE_TIME, T.NEXT_FIRE_TIME, T.MISFIRE_INSTR,
                   C.CRON_EXPRESSION,
                   S.REPEAT_INTERVAL, S.REPEAT_COUNT
            FROM {prefix}JOB_DETAILS J
            LEFT JOIN {prefix}TRIGGERS T
                   ON J.JOB_NAME = T.JOB_NAME AND J.JOB_GROUP = T.JOB_GROUP
            LEFT JOIN {prefix}CRON_TRIGGERS C
                   ON T.TRIGGER_NAME = C.TRIGGER_NAME AND T.TRIGGER_GROUP = C.TRIGGER_GROUP
            LEFT JOIN {prefix}SIMPLE_TRIGGERS S
                   ON T.TRIGGER_NAME = S.TRIGGER_NAME AND T.TRIGGER_GROUP = S.TRIGGER_GROUP
            WHERE J.JOB_NAME = ? AND J.JOB_GROUP = ?
            """;

    static final String SELECT_TRIGGERS = """
            SELECT T.TRIGGER_NAME, T.TRIGGER_GROUP, T.JOB_NAME, T.JOB_GROUP,
                   T.TRIGGER_STATE, T.TRIGGER_TYPE, T.PREV_FIRE_TIME, T.NEXT_FIRE_TIME,
                   T.MISFIRE_INSTR, C.CRON_EXPRESSION, S.REPEAT_INTERVAL, S.REPEAT_COUNT
            FROM {prefix}TRIGGERS T
            LEFT JOIN {prefix}CRON_TRIGGERS C
                   ON T.TRIGGER_NAME = C.TRIGGER_NAME AND T.TRIGGER_GROUP = C.TRIGGER_GROUP
            LEFT JOIN {prefix}SIMPLE_TRIGGERS S
                   ON T.TRIGGER_NAME = S.TRIGGER_NAME AND T.TRIGGER_GROUP = S.TRIGGER_GROUP
            """;

    static final String SELECT_TRIGGER_BY_KEY = """
            SELECT T.TRIGGER_NAME, T.TRIGGER_GROUP, T.JOB_NAME, T.JOB_GROUP,
                   T.TRIGGER_STATE, T.TRIGGER_TYPE, T.PREV_FIRE_TIME, T.NEXT_FIRE_TIME,
                   T.MISFIRE_INSTR, C.CRON_EXPRESSION, S.REPEAT_INTERVAL, S.REPEAT_COUNT
            FROM {prefix}TRIGGERS T
            LEFT JOIN {prefix}CRON_TRIGGERS C
                   ON T.TRIGGER_NAME = C.TRIGGER_NAME AND T.TRIGGER_GROUP = C.TRIGGER_GROUP
            LEFT JOIN {prefix}SIMPLE_TRIGGERS S
                   ON T.TRIGGER_NAME = S.TRIGGER_NAME AND T.TRIGGER_GROUP = S.TRIGGER_GROUP
            WHERE T.TRIGGER_NAME = ? AND T.TRIGGER_GROUP = ?
            """;

    static final String SELECT_FIRED_TRIGGERS = """
            SELECT ENTRY_ID, SCHED_NAME, JOB_NAME, JOB_GROUP,
                   TRIGGER_NAME, TRIGGER_GROUP, FIRED_TIME, SCHED_TIME,
                   PRIORITY, STATE, INSTANCE_NAME
            FROM {prefix}FIRED_TRIGGERS
            """;

    static final String SELECT_SCHEDULER_STATE = """
            SELECT SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME, CHECKIN_INTERVAL
            FROM {prefix}SCHEDULER_STATE
            """;

    /**
     * Returns job, trigger, and fired-trigger counts in one query.
     * Column aliases: {@code JOB_COUNT}, {@code TRIGGER_COUNT}, {@code FIRED_COUNT}.
     */
    static final String SELECT_COUNTS = """
            SELECT
              (SELECT COUNT(*) FROM {prefix}JOB_DETAILS)    AS JOB_COUNT,
              (SELECT COUNT(*) FROM {prefix}TRIGGERS)        AS TRIGGER_COUNT,
              (SELECT COUNT(*) FROM {prefix}FIRED_TRIGGERS)  AS FIRED_COUNT
            """;

    static String withPrefix(String sql, String prefix) {
        return sql.replace(TABLE_PREFIX_PLACEHOLDER, prefix);
    }
}