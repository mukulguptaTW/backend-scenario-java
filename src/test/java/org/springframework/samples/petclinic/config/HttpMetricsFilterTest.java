/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.config;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HttpMetricsFilter}.
 *
 * Uses an in-process {@link SimpleMeterRegistry} – no Spring context is started.
 */
class HttpMetricsFilterTest {

    private SimpleMeterRegistry registry;
    private HttpMetricsFilter   filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter   = new HttpMetricsFilter(registry);
    }

    // -----------------------------------------------------------------------
    // Successful request – 200 OK
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("200 OK request records SUCCESS outcome and duration > 0")
    void successfulRequest_recordsSuccessMetric() throws Exception {
        MockHttpServletRequest  request  = buildRequest("GET", "/api/owners", 200);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        FilterChain chain = (req, res) -> { /* no-op – simulates controller */ };

        filter.doFilterInternal(request, response, chain);

        Timer timer = findTimer("GET", "SUCCESS");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // Client error – 404 Not Found
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("404 response records CLIENT_ERROR outcome")
    void notFoundResponse_recordsClientError() throws Exception {
        MockHttpServletRequest  request  = buildRequest("GET", "/api/owners/9999", 404);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);

        FilterChain chain = (req, res) -> { /* no-op */ };

        filter.doFilterInternal(request, response, chain);

        Timer timer = findTimer("GET", "CLIENT_ERROR");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Server error – 500 Internal Server Error
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("500 response records SERVER_ERROR outcome")
    void serverErrorResponse_recordsServerError() throws Exception {
        MockHttpServletRequest  request  = buildRequest("POST", "/api/owners", 500);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        FilterChain chain = (req, res) -> { /* no-op */ };

        filter.doFilterInternal(request, response, chain);

        Timer timer = findTimer("POST", "SERVER_ERROR");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Chain throws – exception must propagate AND metric must still be recorded
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("When filter chain throws ServletException the metric is still recorded")
    void chainThrowsServletException_metricIsRecordedAndExceptionPropagates() {
        MockHttpServletRequest  request  = buildRequest("GET", "/api/pets", 200);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain failingChain = (req, res) -> {
            throw new ServletException("simulated downstream failure");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, failingChain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("simulated downstream failure");

        // A SERVER_ERROR timer must still have been recorded despite the exception.
        Timer timer = findTimer("GET", "SERVER_ERROR");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Actuator path – must be skipped entirely
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Requests to /actuator/** are not filtered (shouldNotFilter returns true)")
    void actuatorPath_isNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setServletPath("/actuator/prometheus");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Regular API paths are filtered (shouldNotFilter returns false)")
    void apiPath_isFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/owners");
        request.setServletPath("/api/owners");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Multiple requests – counter accumulates
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Multiple successful requests accumulate in the same timer")
    void multipleRequests_counterAccumulates() throws Exception {
        FilterChain chain = (req, res) -> { /* no-op */ };

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest  request  = buildRequest("GET", "/api/vets", 200);
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(200);
            filter.doFilterInternal(request, response, chain);
        }

        Timer timer = findTimer("GET", "SUCCESS");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a MockHttpServletRequest with a servlet path. */
    private MockHttpServletRequest buildRequest(String method, String path, int responseStatus) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        return req;
    }

    /**
     * Finds the {@link Timer} for the given method + outcome combination in the
     * {@link SimpleMeterRegistry}.  Returns {@code null} when not found.
     */
    private Timer findTimer(String method, String outcome) {
        return registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("method",  method)
                .tag("outcome", outcome)
                .timer();
    }
}
