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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Servlet filter that records HTTP request metrics into Micrometer's
 * {@link MeterRegistry} for every inbound request.
 *
 * <h3>What is measured</h3>
 * <ul>
 *   <li><strong>Request rate</strong> – derived from the {@code _count} suffix of
 *       the {@code http.server.requests} timer. Use
 *       {@code rate(http_server_requests_seconds_count[1m])} in Prometheus.</li>
 *   <li><strong>Error rate</strong> – filter on the {@code outcome} tag
 *       ({@code CLIENT_ERROR} / {@code SERVER_ERROR}) of the same counter.</li>
 *   <li><strong>Request duration (histogram)</strong> – recorded as a
 *       {@link Timer} with histogram buckets enabled via
 *       {@code management.metrics.distribution.percentiles-histogram
 *       .http.server.requests=true}. Bucket boundaries are controlled by the
 *       {@code management.metrics.distribution.slo.http.server.requests}
 *       property.</li>
 * </ul>
 *
 * <h3>Tags on every sample</h3>
 * <pre>
 *   method   – HTTP verb  (GET, POST, PUT, DELETE, …)
 *   uri      – best-effort templated path (/api/owners/{ownerId}) or the
 *              raw servlet path when no handler match is available
 *   status   – HTTP status code as a string (200, 404, 500, …)
 *   outcome  – SUCCESS | CLIENT_ERROR | SERVER_ERROR | UNKNOWN
 * </pre>
 *
 * <h3>Skipped paths</h3>
 * Requests to {@code /actuator/**} are excluded to avoid self-referential
 * noise in the metrics.
 *
 * <h3>Note on double-counting</h3>
 * Spring Boot's auto-configured {@code WebMvcMetricsFilter} already
 * instruments {@code http.server.requests} when
 * {@code micrometer-registry-prometheus} is on the classpath.  This filter
 * writes to a <em>separate</em> metric name
 * ({@code petclinic.http.server.requests}) so that teams can compare or
 * gradually migrate without conflict.  Remove this filter once the auto-
 * configured metric fully satisfies your requirements.
 */
@Component
public class HttpMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpMetricsFilter.class);

    /** Metric name written by this filter. */
    static final String METRIC_NAME = "petclinic.http.server.requests";

    /** URI used when no route match (or actuator) was resolved. */
    private static final String URI_UNKNOWN  = "UNKNOWN";
    private static final String URI_ACTUATOR = "/actuator";

    private final MeterRegistry meterRegistry;

    public HttpMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Skip actuator endpoints – they are infrastructure, not business traffic,
     * and including them skews request-rate / error-rate dashboards.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && path.startsWith(URI_ACTUATOR);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();
        Throwable caught = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException ex) {
            caught = ex;
            throw ex;
        } finally {
            recordMetric(request, response, startNanos, caught);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void recordMetric(HttpServletRequest  request,
                               HttpServletResponse response,
                               long               startNanos,
                               Throwable          error) {
        try {
            String method  = request.getMethod();
            String uri     = resolveUri(request);
            int    status  = resolveStatus(response, error);
            String outcome = toOutcome(status);

            Timer.builder(METRIC_NAME)
                    .description("HTTP request rate, error rate, and duration histogram")
                    .tag("method",  method  != null ? method : "UNKNOWN")
                    .tag("uri",     uri)
                    .tag("status",  String.valueOf(status))
                    .tag("outcome", outcome)
                    // publishPercentileHistogram() makes Prometheus receive bucket data
                    // so histogram_quantile() works accurately server-side.
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        } catch (Exception ex) {
            // Metric recording must never affect the HTTP response.
            log.warn("Failed to record HTTP metrics", ex);
        }
    }

    /**
     * Resolves the best-effort URI template for the tag value.
     *
     * <p>Spring MVC stores the matched pattern (e.g. {@code /api/owners/{ownerId}})
     * as a request attribute after the handler has been resolved.  Before that
     * point we fall back to the raw servlet path so the tag is always present.
     */
    private String resolveUri(HttpServletRequest request) {
        // Spring MVC populates this attribute after DispatcherServlet routing.
        Object pattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        String path = request.getServletPath();
        return (path != null && !path.isBlank()) ? path : URI_UNKNOWN;
    }

    /**
     * Returns the response status, falling back to 500 when the chain threw
     * an exception before a status could be committed.
     */
    private int resolveStatus(HttpServletResponse response, Throwable error) {
        if (error != null) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR; // 500
        }
        int status = response.getStatus();
        // Servlet containers return 0 when no status has been set yet.
        return status > 0 ? status : HttpServletResponse.SC_OK;
    }

    /**
     * Converts an HTTP status code into a human-readable outcome label that
     * mirrors the convention used by Spring Boot's built-in
     * {@code WebMvcMetricsFilter}.
     */
    private String toOutcome(int status) {
        if (status >= 500) return "SERVER_ERROR";
        if (status >= 400) return "CLIENT_ERROR";
        if (status >= 200) return "SUCCESS";
        return "UNKNOWN";
    }
}
