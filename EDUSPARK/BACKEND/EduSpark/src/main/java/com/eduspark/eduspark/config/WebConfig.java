package com.eduspark.eduspark.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Web 配置。
 */
@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        factory.setBufferRequestBody(true);
        return new RestTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("jwtAuthenticationFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorsFilter(parseAllowedOrigins(allowedOrigins)));
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        registration.setName("corsFilter");
        return registration;
    }

    private Set<String> parseAllowedOrigins(String configuredOrigins) {
        Set<String> allowedOrigins = new LinkedHashSet<>();
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return allowedOrigins;
        }
        for (String value : configuredOrigins.split(",")) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isBlank()) {
                allowedOrigins.add(normalized);
            }
        }
        return allowedOrigins;
    }

    public static class CorsFilter implements Filter {
        private final Set<String> allowedOrigins;

        public CorsFilter(Set<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            String origin = request.getHeader("Origin");
            boolean corsRequest = origin != null && !origin.isBlank();
            boolean allowedOrigin = corsRequest && isAllowedOrigin(origin);

            if (corsRequest && allowedOrigin) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Vary", "Origin");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin, Enctype");
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Max-Age", "3600");
            }

            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(allowedOrigin || !corsRequest
                        ? HttpServletResponse.SC_OK
                        : HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            chain.doFilter(req, res);
        }

        private boolean isAllowedOrigin(String origin) {
            if (origin == null || origin.isBlank()) {
                return false;
            }
            if (allowedOrigins.contains(origin)) {
                return true;
            }
            try {
                URI uri = URI.create(origin);
                String host = uri.getHost();
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
