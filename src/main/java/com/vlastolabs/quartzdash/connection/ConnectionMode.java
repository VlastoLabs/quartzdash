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
package  com.vlastolabs.quartzdash.connection;

/**
 * Sealed interface representing a connection mode to a Quartz Scheduler.
 * QuartzDash can connect either via JDBC
 */
public sealed interface ConnectionMode
        permits ConnectionMode.JdbcConnectionMode {

    /**
     * JDBC connection mode - connects to the monitored app's database.
     *
     * @param url                 JDBC URL of the monitored application's database
     * @param username            database username
     * @param password            database password
     * @param tablePrefix         prefix for Quartz tables (default: QRTZ_)
     * @param driverDelegateClass Quartz JDBC delegate class
     */
    record JdbcConnectionMode(
            String url,
            String username,
            String password,
            String tablePrefix,
            String driverDelegateClass
    ) implements ConnectionMode {
        public JdbcConnectionMode {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("JDBC URL cannot be null or blank");
            }
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Database username cannot be null or blank");
            }
            if (password == null) {
                password = "";
            }
            if (tablePrefix == null || tablePrefix.isBlank()) {
                tablePrefix = "QRTZ_";
            }
            if (driverDelegateClass == null || driverDelegateClass.isBlank()) {
                driverDelegateClass = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
            }
        }
    }
}
