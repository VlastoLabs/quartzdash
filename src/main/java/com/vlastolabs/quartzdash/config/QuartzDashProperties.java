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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for QuartzDash.
 *
 * @param connection connection configuration (JDBC or JMX)
 * @param security   security configuration
 */
@ConfigurationProperties("quartzdash")
@Validated
public record QuartzDashProperties(
        @Valid ConnectionConfig connection,
        @Valid SecurityConfig security
) {

    public record ConnectionConfig(
            @NotNull ConnectionMode mode,
            @Valid JdbcConfig jdbc) {
    }

    public enum ConnectionMode {
        JDBC
    }

    public record JdbcConfig(
            @NotBlank String url,
            @NotBlank String username,
            @NotBlank String password,
            String tablePrefix,
            String driverDelegateClass
    ) {
        public JdbcConfig {
            if (tablePrefix == null || tablePrefix.isBlank()) {
                tablePrefix = "QRTZ_";
            }
            if (driverDelegateClass == null || driverDelegateClass.isBlank()) {
                driverDelegateClass = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
            }
        }
    }

    public record SecurityConfig(
            @NotBlank String username,
            @NotBlank String password
    ) {
        public SecurityConfig {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException(
                        "quartzdash.security.password is required and cannot be blank. " +
                                "Set it via environment variable QUARTZDASH_SECURITY_PASSWORD"
                );
            }
        }
    }
}
