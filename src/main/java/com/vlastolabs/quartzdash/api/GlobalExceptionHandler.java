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
package  com.vlastolabs.quartzdash.api;

import com.vlastolabs.quartzdash.exception.ConnectionException;
import com.vlastolabs.quartzdash.exception.JobNotFoundException;
import com.vlastolabs.quartzdash.exception.QuartzDashException;
import com.vlastolabs.quartzdash.exception.TriggerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for REST API endpoints.
 * Returns RFC 7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_BASE_URI = "";

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException ex) {
        ProblemDetail problem = buildProblem(HttpStatus.NOT_FOUND, "Job Not Found", ex.getMessage(), "job-not-found");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(TriggerNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTriggerNotFound(TriggerNotFoundException ex) {
        ProblemDetail problem = buildProblem(HttpStatus.NOT_FOUND, "Trigger Not Found", ex.getMessage(), "trigger-not-found");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<ProblemDetail> handleConnectionException(ConnectionException ex) {
        log.error("Connection error", ex);
        ProblemDetail problem = buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Scheduler Connection Error",
                "Failed to connect to Quartz Scheduler: " + ex.getMessage(),
                "connection-error"
        );
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(QuartzDashException.class)
    public ResponseEntity<ProblemDetail> handleQuartzDashException(QuartzDashException ex) {
        log.error("QuartzDash error", ex);
        ProblemDetail problem = buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "QuartzDash Error", ex.getMessage(), "quartzdash-error");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage(), "invalid-request");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex) {
        String detail = "Required parameter '%s' of type '%s' is missing".formatted(ex.getParameterName(), ex.getParameterType());
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Missing Parameter", detail, "invalid-request");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Parameter '%s' has invalid value '%s'".formatted(ex.getName(), ex.getValue());
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Invalid Parameter Type", detail, "invalid-request");
        return ResponseEntity.of(problem).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                "internal-error"
        );
        return ResponseEntity.of(problem).build();
    }

    private ProblemDetail buildProblem(HttpStatus status, String title, String detail, String errorSlug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://www.rfc-editor.org/rfc/rfc7807"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}