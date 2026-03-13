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
package  com.vlastolabs.quartzdash.config;

import com.vlastolabs.quartzdash.connection.ConnectionMode;
import com.vlastolabs.quartzdash.connection.ConnectionModeFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for QuartzDash data sources.
 * <p>
 * Provides a single data source that connects to the monitored application's Quartz database.
 * All data (jobs, triggers, history, misfires) is read directly from the Quartz tables.
 */
@Configuration
@EnableConfigurationProperties(QuartzDashProperties.class)
public class DataSourceConfig {

    private final QuartzDashProperties properties;
    private final ConnectionModeFactory connectionModeFactory;

    public DataSourceConfig(QuartzDashProperties properties,
                            ConnectionModeFactory connectionModeFactory) {
        this.properties = properties;
        this.connectionModeFactory = connectionModeFactory;
    }

    /**
     * Creates the DataSource for connecting to the monitored application's database.
     * Only created when connection mode is JDBC.
     *
     * @return DataSource for the monitored app's database
     */
    @Bean
    public DataSource quartzDataSource() {
        ConnectionMode mode = connectionModeFactory.create();
        if (mode instanceof ConnectionMode.JdbcConnectionMode jdbcMode) {
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            ds.setUrl(jdbcMode.url());
            ds.setUsername(jdbcMode.username());
            ds.setPassword(jdbcMode.password());
            ds.setDriverClassName(determineDriverClass(jdbcMode.url()));
            return ds;
        }
        throw new IllegalStateException(
                "quartzDataSource is only available in JDBC mode. Current mode: " + mode.getClass().getSimpleName()
        );
    }

    /**
     * Creates JdbcTemplate for the monitored app's database.
     * This is the primary JdbcTemplate used throughout the application.
     *
     * @param quartzDataSource the DataSource for the monitored app's database
     * @return JdbcTemplate for querying Quartz tables
     */
    @Bean
    @Primary
    public JdbcTemplate quartzJdbcTemplate(DataSource quartzDataSource) {
        return new JdbcTemplate(quartzDataSource);
    }

    private String determineDriverClass(String url) {
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        } else if (url.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (url.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (url.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (url.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        // Default to PostgreSQL
        return "org.postgresql.Driver";
    }
}
