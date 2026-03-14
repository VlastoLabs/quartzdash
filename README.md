# QuartzDash

[![Java](https://img.shields.io/badge/Java-25-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.X-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![CI](https://github.com/VlastoLabs/quartzdash/actions/workflows/ci.yml/badge.svg)

**QuartzDash Community Edition** is a monitoring backend service for Quartz Scheduler instances running in external Java applications. It connects to your application's existing database (JDBC) and exposes a REST API for job observability — with no changes required to the monitored application.

> **Looking for a UI?** The QuartzDash Workspace project provides a full dashboard that connects to this API.
---

## Features

- **Real-time job monitoring** — reads directly from your existing `QRTZ_*` tables via JDBC, zero agent required

---

## Quick Start

### Prerequisites

- Java 25+ (local development only)
- Maven 4.0+ (local development only)
- A running application with Quartz Scheduler configured to use a JDBC `JobStore`

QuartzDash starts on port `8080`. Default credentials: `admin` / `admin`.

> **Production:** always override the password via `QUARTZDASH_SECURITY_PASSWORD` — see [Configuration](#configuration).

### Pointing at Your Database

```yaml
environment:
  QUARTZDASH_CONNECTION_JDBC_URL: jdbc:postgresql://your-db-host:5432/yourdb
  QUARTZDASH_CONNECTION_JDBC_USERNAME: youruser
  QUARTZDASH_CONNECTION_JDBC_PASSWORD: yourpassword
  QUARTZDASH_SECURITY_PASSWORD: change-me
```

QuartzDash reads from your existing `QRTZ_*` tables. Your Quartz schema is never modified.

---

## Configuration

### Full `application.yml` Reference

```yaml
server:
  port: 8080

quartzdash:
  connection:
    mode: jdbc                             
    jdbc:
      url: jdbc:postgresql://localhost:5432/myapp
      username: myuser
      password: mypassword
      table-prefix: QRTZ_                  
      driver-delegate-class: org.quartz.impl.jdbcjobstore.StdJDBCDelegate

  security:
    username: admin
    password: ${QUARTZDASH_SECURITY_PASSWORD}
    
spring:
  threads:
    virtual:
      enabled: true

logging:
  level:
    root: INFO
    com.quartzdash: DEBUG
    org.quartz: WARN
    org.springframework.jdbc: TRACE
```

### Environment Variables

All `quartzdash.*` properties can be overridden via environment variables using Spring Boot's relaxed binding (`QUARTZDASH_CONNECTION_JDBC_URL`, `QUARTZDASH_SECURITY_PASSWORD`, etc.).

| Variable | Description |
|---|---|
| `QUARTZDASH_SECURITY_PASSWORD` | Admin password. Application **will not start** if unset. |
| `QUARTZDASH_CONNECTION_JDBC_URL` | JDBC URL of your monitored application's database |
| `QUARTZDASH_CONNECTION_JDBC_USERNAME` | Database username |
| `QUARTZDASH_CONNECTION_JDBC_PASSWORD` | Database password |
| `QUARTZDASH_CONNECTION_JDBC_TABLE_PREFIX` | Quartz table prefix (default: `QRTZ_`) |

---
# Supported Databases

| Database | Version | Compatibility | `driver-delegate-class` |
|---|---|---|---|
| **PostgreSQL** | 12+ | ✅ Full | `org.quartz.impl.jdbcjobstore.StdJDBCDelegate` |
| **MySQL** | 8.0+ | ✅ Full | `org.quartz.impl.jdbcjobstore.StdJDBCDelegate` |
| **MariaDB** | 10.5+ | ✅ Full | `org.quartz.impl.jdbcjobstore.StdJDBCDelegate` |
| **Oracle** | 12c+ | ✅ Full | `org.quartz.impl.jdbcjobstore.oracle.OracleDelegate` |
| **SQL Server** | 2017+ | ✅ Full | `org.quartz.impl.jdbcjobstore.MSSQLDelegate` |
| **H2** | 2.x | ✅ Full | `org.quartz.impl.jdbcjobstore.StdJDBCDelegate` |
| **DB2** | 11.5+ | ✅ Full | `org.quartz.impl.jdbcjobstore.DB2v8Delegate` |
---
## REST API Documentation

The REST API is documented using [OpenAPI 3](https://swagger.io/specification/).

- **Spec file:** [`docs/openapi.yaml`](docs/openapi.yaml)

### Viewing the docs

**Swagger UI (online):**
Paste the raw file URL into [editor.swagger.io](https://editor.swagger.io/).

## How It Works

QuartzDash reads directly from the `QRTZ_*` tables that Quartz maintains in your application's database. No agent, no code changes, no additional dependencies in your application.

```
Your Application              Your Database             QuartzDash
──────────────                ─────────────             ──────────
Quartz Scheduler  ──writes──► QRTZ_JOB_DETAILS  ◄─reads─ SchedulerPoller
                              QRTZ_TRIGGERS               │
                              QRTZ_FIRED_TRIGGERS         ├─► REST API
                              QRTZ_SCHEDULER_STATE        ├─► Metrics
                              QRTZ_CRON_TRIGGERS           
                              QRTZ_LOCKS                   
                                                               
```

## Building from Source

```bash
git clone https://github.com/your-org/quartzdash-community
cd quartzdash-community

# Build
mvn clean package -DskipTests

# Run
QUARTZDASH_SECURITY_PASSWORD=admin \
QUARTZDASH_CONNECTION_JDBC_URL=jdbc:postgresql://localhost:5432/myapp \
QUARTZDASH_CONNECTION_JDBC_USERNAME=myuser \
QUARTZDASH_CONNECTION_JDBC_PASSWORD=mypassword \
java -jar target/quartzdash-community-*.jar
```
---

## Contributing
Issues and pull requests are welcome. Please open a GitHub issue before starting significant work so we can discuss the approach.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for the full text.