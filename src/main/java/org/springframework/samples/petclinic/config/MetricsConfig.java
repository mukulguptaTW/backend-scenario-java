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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer / Prometheus metrics configuration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Adds a common {@code application} tag to <em>every</em> metric so that
 *       Prometheus queries can filter by service name.</li>
 *   <li>The HTTP request-rate, error-rate, and duration histogram are provided
 *       automatically by Spring Boot's {@code WebMvcMetricsFilter} once
 *       {@code micrometer-registry-prometheus} is on the classpath.
 *       The histogram bucket configuration is driven by
 *       {@code management.metrics.distribution.*} properties in
 *       {@code application.properties}.</li>
 * </ul>
 *
 * <p>Metrics exposed at {@code /actuator/prometheus}:
 * <pre>
 *   http_server_requests_seconds_count   – total request count  (rate)
 *   http_server_requests_seconds_sum     – total duration sum
 *   http_server_requests_seconds_bucket  – histogram buckets     (duration + SLOs)
 *   http_server_requests_seconds_max     – max observed duration
 * </pre>
 *
 * Tags on every {@code http_server_requests_*} sample:
 * <pre>
 *   application – spring.application.name (petclinic-rest)
 *   method      – HTTP verb  (GET, POST, …)
 *   uri         – templated URI  (/api/owners/{ownerId})
 *   status      – HTTP status code  (200, 404, 500, …)
 *   outcome     – SUCCESS | CLIENT_ERROR | SERVER_ERROR | UNKNOWN
 *   exception   – exception class name, or "none"
 * </pre>
 *
 * Useful Prometheus queries:
 * <pre>
 *   # Request rate (req/s over last 1 minute)
 *   rate(http_server_requests_seconds_count{application="petclinic-rest"}[1m])
 *
 *   # Error rate  (4xx + 5xx req/s)
 *   rate(http_server_requests_seconds_count{application="petclinic-rest",
 *        outcome=~"CLIENT_ERROR|SERVER_ERROR"}[1m])
 *
 *   # 95th-percentile latency
 *   histogram_quantile(0.95,
 *     rate(http_server_requests_seconds_bucket{application="petclinic-rest"}[5m]))
 * </pre>
 */
@Configuration
public class MetricsConfig {

    /**
     * Adds the {@code application} common tag to every meter registered in the
     * global {@link MeterRegistry}.  The value is read from
     * {@code spring.application.name} (defaults to {@code "petclinic-rest"} when
     * not set).
     *
     * @param applicationName the logical service name
     * @return a {@link MeterRegistryCustomizer} that applies the tag
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.application.name:petclinic-rest}") String applicationName) {
        return registry -> registry.config()
                .commonTags("application", applicationName);
    }
}
