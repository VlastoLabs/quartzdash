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
package  com.vlastolabs.quartzdash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * QuartzDash Community Edition - Quartz Scheduler Monitoring and Management Backend.
 * <p>
 * This application connects to external Quartz Scheduler instances via JDBC or JMX
 * and provides REST API endpoints for job observability and basic management.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class QuartzDashApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuartzDashApplication.class, args);
    }
}
