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
import com.vlastolabs.quartzdash.scheduler.JdbcSchedulerReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JdbcSchedulerReader against PostgreSQL with Quartz schema.
 */
@SpringBootTest
@Testcontainers
@DisplayName("JdbcSchedulerReader")
class JdbcSchedulerReaderTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("quartz_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("quartzdash.connection.jdbc.url", postgres::getJdbcUrl);
        registry.add("quartzdash.connection.jdbc.username", postgres::getUsername);
        registry.add("quartzdash.connection.jdbc.password", postgres::getPassword);
        registry.add("quartzdash.connection.mode", () -> "jdbc");
    }

    @Autowired
    private JdbcTemplate quartzJdbcTemplate;

    @Autowired
    private QuartzDashProperties properties;

    private JdbcSchedulerReader schedulerReader;

    @BeforeEach
    void setUp() {
        setupQuartzSchema();
        schedulerReader = new JdbcSchedulerReader(quartzJdbcTemplate, properties);
    }

    private void setupQuartzSchema() {
        // Create tables
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_JOB_DETAILS (SCHED_NAME VARCHAR(120) NOT NULL, JOB_NAME VARCHAR(200) NOT NULL, JOB_GROUP VARCHAR(200) NOT NULL, DESCRIPTION VARCHAR(250), JOB_CLASS_NAME VARCHAR(250) NOT NULL, IS_DURABLE BOOLEAN NOT NULL, IS_NONCONCURRENT BOOLEAN NOT NULL, IS_UPDATE_DATA BOOLEAN NOT NULL, REQUESTS_RECOVERY BOOLEAN NOT NULL, JOB_DATA BYTEA, PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP))");
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_TRIGGERS (SCHED_NAME VARCHAR(120) NOT NULL, TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, JOB_NAME VARCHAR(200) NOT NULL, JOB_GROUP VARCHAR(200) NOT NULL, DESCRIPTION VARCHAR(250), NEXT_FIRE_TIME BIGINT, PREV_FIRE_TIME BIGINT, PRIORITY INTEGER, TRIGGER_STATE VARCHAR(16) NOT NULL, TRIGGER_TYPE VARCHAR(8) NOT NULL, START_TIME BIGINT NOT NULL, END_TIME BIGINT, CALENDAR_NAME VARCHAR(200), MISFIRE_INSTR SMALLINT, JOB_DATA BYTEA, PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP))");
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_CRON_TRIGGERS (SCHED_NAME VARCHAR(120) NOT NULL, TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, CRON_EXPRESSION VARCHAR(120) NOT NULL, TIME_ZONE_ID VARCHAR(80), PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP))");
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_SIMPLE_TRIGGERS (SCHED_NAME VARCHAR(120) NOT NULL, TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, REPEAT_COUNT BIGINT NOT NULL, REPEAT_INTERVAL BIGINT NOT NULL, TIMES_TRIGGERED BIGINT NOT NULL, PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP))");
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_FIRED_TRIGGERS (SCHED_NAME VARCHAR(120) NOT NULL, ENTRY_ID VARCHAR(95) NOT NULL, TRIGGER_NAME VARCHAR(200) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, INSTANCE_NAME VARCHAR(200) NOT NULL, FIRED_TIME BIGINT NOT NULL, SCHED_TIME BIGINT NOT NULL, PRIORITY INTEGER NOT NULL, STATE VARCHAR(16) NOT NULL, JOB_NAME VARCHAR(200), JOB_GROUP VARCHAR(200), IS_NONCONCURRENT BOOLEAN, REQUESTS_RECOVERY BOOLEAN, PRIMARY KEY (SCHED_NAME, ENTRY_ID))");
        quartzJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS QRTZ_SCHEDULER_STATE (SCHED_NAME VARCHAR(120) NOT NULL, INSTANCE_NAME VARCHAR(200) NOT NULL, LAST_CHECKIN_TIME BIGINT NOT NULL, CHECKIN_INTERVAL BIGINT NOT NULL, PRIMARY KEY (SCHED_NAME, INSTANCE_NAME))");

        // Clean up and insert test data
        quartzJdbcTemplate.update("DELETE FROM QRTZ_SCHEDULER_STATE");
        quartzJdbcTemplate.update("DELETE FROM QRTZ_SIMPLE_TRIGGERS");
        quartzJdbcTemplate.update("DELETE FROM QRTZ_CRON_TRIGGERS");
        quartzJdbcTemplate.update("DELETE FROM QRTZ_TRIGGERS");
        quartzJdbcTemplate.update("DELETE FROM QRTZ_JOB_DETAILS");

        insertTestData();
    }

    private void insertTestData() {
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();

        // Insert jobs
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP, DESCRIPTION, JOB_CLASS_NAME, IS_DURABLE, IS_NONCONCURRENT, IS_UPDATE_DATA, REQUESTS_RECOVERY, JOB_DATA) VALUES ('QuartzScheduler', 'TestJob1', 'DEFAULT', 'Test Job 1', 'com.example.TestJob1', TRUE, FALSE, FALSE, FALSE, NULL)");
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP, DESCRIPTION, JOB_CLASS_NAME, IS_DURABLE, IS_NONCONCURRENT, IS_UPDATE_DATA, REQUESTS_RECOVERY, JOB_DATA) VALUES ('QuartzScheduler', 'TestJob2', 'DEFAULT', 'Test Job 2', 'com.example.TestJob2', FALSE, FALSE, FALSE, TRUE, NULL)");
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP, DESCRIPTION, JOB_CLASS_NAME, IS_DURABLE, IS_NONCONCURRENT, IS_UPDATE_DATA, REQUESTS_RECOVERY, JOB_DATA) VALUES ('QuartzScheduler', 'TestJob3', 'GROUP1', 'Test Job 3', 'com.example.TestJob3', TRUE, TRUE, FALSE, FALSE, NULL)");

        // Insert triggers
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, JOB_NAME, JOB_GROUP, DESCRIPTION, NEXT_FIRE_TIME, PREV_FIRE_TIME, PRIORITY, TRIGGER_STATE, TRIGGER_TYPE, START_TIME, END_TIME, MISFIRE_INSTR) VALUES ('QuartzScheduler', 'TestTrigger1', 'DEFAULT', 'TestJob1', 'DEFAULT', 'Test Trigger 1', ?, ?, 5, 'WAITING', 'CRON', ?, NULL, 0)",
                nowMs + 5000, nowMs - 5000, nowMs - 10000);
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, JOB_NAME, JOB_GROUP, DESCRIPTION, NEXT_FIRE_TIME, PREV_FIRE_TIME, PRIORITY, TRIGGER_STATE, TRIGGER_TYPE, START_TIME, END_TIME, MISFIRE_INSTR) VALUES ('QuartzScheduler', 'TestTrigger2', 'DEFAULT', 'TestJob2', 'DEFAULT', 'Test Trigger 2', ?, ?, 5, 'PAUSED', 'CRON', ?, NULL, 0)",
                nowMs + 10000, nowMs - 10000, nowMs - 20000);
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, JOB_NAME, JOB_GROUP, DESCRIPTION, NEXT_FIRE_TIME, PREV_FIRE_TIME, PRIORITY, TRIGGER_STATE, TRIGGER_TYPE, START_TIME, END_TIME, MISFIRE_INSTR) VALUES ('QuartzScheduler', 'TestTrigger3', 'GROUP1', 'TestJob3', 'GROUP1', 'Test Trigger 3', ?, ?, 5, 'ERROR', 'SIMPLE', ?, NULL, 1)",
                nowMs - 60000, nowMs - 120000, nowMs - 30000);

        // Insert cron triggers
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_CRON_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, CRON_EXPRESSION, TIME_ZONE_ID) VALUES ('QuartzScheduler', 'TestTrigger1', 'DEFAULT', '0/5 * * * * ?', 'UTC')");
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_CRON_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, CRON_EXPRESSION, TIME_ZONE_ID) VALUES ('QuartzScheduler', 'TestTrigger2', 'DEFAULT', '0/10 * * * * ?', 'UTC')");

        // Insert simple triggers
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_SIMPLE_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, REPEAT_COUNT, REPEAT_INTERVAL, TIMES_TRIGGERED) VALUES ('QuartzScheduler', 'TestTrigger3', 'GROUP1', -1, 5000, 100)");

        // Insert scheduler state
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_SCHEDULER_STATE (SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME, CHECKIN_INTERVAL) VALUES ('QuartzScheduler', 'instance1', ?, 7500)", nowMs);
        quartzJdbcTemplate.update(
                "INSERT INTO QRTZ_SCHEDULER_STATE (SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME, CHECKIN_INTERVAL) VALUES ('QuartzScheduler', 'instance2', ?, 7500)", nowMs - 10000);
    }

    @Nested
    @DisplayName("getSchedulerStatus()")
    class GetSchedulerStatus {

        @Test
        @DisplayName("should_return_scheduler_status_with_job_counts")
        @Transactional
        void should_return_scheduler_status_with_job_counts() {
            SchedulerStatus status = schedulerReader.getSchedulerStatus();

            assertThat(status.schedulerName()).isEqualTo("QuartzScheduler");
            assertThat(status.totalJobs()).isEqualTo(3);
            assertThat(status.totalTriggers()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getAllJobs()")
    class GetAllJobs {

        @Test
        @DisplayName("should_return_all_jobs")
        @Transactional
        void should_return_all_jobs() {
            List<JobSummary> jobs = schedulerReader.getAllJobs();

            assertThat(jobs).hasSize(3);
            assertThat(jobs.stream().map(j -> j.key().name()))
                    .containsExactlyInAnyOrder("TestJob1", "TestJob2", "TestJob3");
        }

        @Test
        @DisplayName("should_filter_jobs_by_group")
        @Transactional
        void should_filter_jobs_by_group() {
            List<JobSummary> jobs = schedulerReader.getJobsByGroup("GROUP1");

            assertThat(jobs).hasSize(1);
            assertThat(jobs.get(0).key().name()).isEqualTo("TestJob3");
        }
    }

    @Nested
    @DisplayName("getJobByKey()")
    class GetJobByKey {

        @Test
        @DisplayName("should_return_job_when_exists")
        @Transactional
        void should_return_job_when_exists() {
            var result = schedulerReader.getJobByKey(new JobKey("DEFAULT", "TestJob1"));

            assertThat(result).isPresent();
            assertThat(result.get().jobClass()).isEqualTo("com.example.TestJob1");
            assertThat(result.get().durable()).isTrue();
        }

        @Test
        @DisplayName("should_return_empty_when_job_not_found")
        @Transactional
        void should_return_empty_when_job_not_found() {
            var result = schedulerReader.getJobByKey(new JobKey("DEFAULT", "NonExistentJob"));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllTriggers()")
    class GetAllTriggers {

        @Test
        @DisplayName("should_return_all_triggers")
        @Transactional
        void should_return_all_triggers() {
            List<TriggerSummary> triggers = schedulerReader.getAllTriggers();

            assertThat(triggers).hasSize(3);
        }

        @Test
        @DisplayName("should_filter_triggers_by_state")
        @Transactional
        void should_filter_triggers_by_state() {
            List<TriggerSummary> triggers = schedulerReader.getTriggersByState(TriggerState.ERROR);

            assertThat(triggers).hasSize(1);
            assertThat(triggers.get(0).key().name()).isEqualTo("TestTrigger3");
        }
    }

    @Nested
    @DisplayName("getClusterNodes()")
    class GetClusterNodes {

        @Test
        @DisplayName("should_return_cluster_nodes")
        @Transactional
        void should_return_cluster_nodes() {
            List<ClusterNode> nodes = schedulerReader.getClusterNodes();

            assertThat(nodes).hasSize(2);
            assertThat(nodes.stream().map(ClusterNode::instanceId))
                    .containsExactlyInAnyOrder("instance1", "instance2");
        }
    }
}
