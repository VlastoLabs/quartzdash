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

import com.vlastolabs.quartzdash.config.QuartzDashProperties;
import org.springframework.stereotype.Component;

/**
 * Factory for creating ConnectionMode instances based on configuration.
 */
@Component
public class ConnectionModeFactory {

    private final QuartzDashProperties properties;

    public ConnectionModeFactory(QuartzDashProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the appropriate ConnectionMode based on configuration.
     *
     * @return a ConnectionMode implementation (JdbcConnectionMode)
     * @throws IllegalStateException if the connection mode is not supported
     */
    public ConnectionMode create() {
        return switch (properties.connection().mode()) {
            case JDBC -> createJdbcMode();
        };
    }

    private ConnectionMode.JdbcConnectionMode createJdbcMode() {
        var jdbc = properties.connection().jdbc();
        return new ConnectionMode.JdbcConnectionMode(
                jdbc.url(),
                jdbc.username(),
                jdbc.password(),
                jdbc.tablePrefix(),
                jdbc.driverDelegateClass()
        );
    }
}
