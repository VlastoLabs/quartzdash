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
import java.time.Instant;
import java.util.UUID;

/**
 * Standard API response wrapper.
 *
 * @param data      the response data
 * @param timestamp when the response was generated
 * @param requestId unique request identifier for tracing
 * @param <T>       the data type
 */
public record ApiResponse<T>(
        T data,
        Instant timestamp,
        String requestId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(data, Instant.now(), requestId);
    }
}
