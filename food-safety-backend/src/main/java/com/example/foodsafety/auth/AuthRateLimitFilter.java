package com.example.foodsafety.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_BUCKETS = 10_000;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public AuthRateLimitFilter(ObjectMapper objectMapper, boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getServletPath().startsWith("/api/auth/") || limitFor(request) == 0;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        Bucket bucket = getBucket(key, now);
        if (bucket == null) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/problem+json");
            objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication request limiter is at capacity. Try again later."
            ));
            return;
        }
        boolean allowed = bucket.tryConsume(now, limitFor(request));
        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/problem+json");
            response.setHeader("Retry-After", Long.toString(bucket.retryAfterSeconds(now)));
            objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many authentication requests. Try again later."
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Bucket getBucket(String key, long now) {
        synchronized (buckets) {
            Bucket existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }
            if (buckets.size() >= MAX_BUCKETS) {
                removeExpiredBuckets(now);
            }
            if (buckets.size() >= MAX_BUCKETS) {
                return null;
            }
            Bucket created = new Bucket(now);
            buckets.put(key, created);
            return created;
        }
    }

    private static int limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && (path.endsWith("/login") || path.endsWith("/password/reset"))) {
            return 10;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && (path.endsWith("/register")
                || path.endsWith("/verification/resend")
                || path.endsWith("/password/forgot"))) {
            return 5;
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.endsWith("/refresh")) {
            return 60;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.endsWith("/verify-email")) {
            return 10;
        }
        return 0;
    }

    private void removeExpiredBuckets(long now) {
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (entry.getValue().isExpired(now)) {
                buckets.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class Bucket {
        private long windowStart;
        private int attempts;

        private Bucket(long windowStart) {
            this.windowStart = windowStart;
        }

        private synchronized boolean tryConsume(long now, int limit) {
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                attempts = 0;
            }
            if (attempts >= limit) {
                return false;
            }
            attempts++;
            return true;
        }

        private synchronized long retryAfterSeconds(long now) {
            return Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
        }

        private synchronized boolean isExpired(long now) {
            return now - windowStart >= WINDOW_MILLIS;
        }
    }
}
