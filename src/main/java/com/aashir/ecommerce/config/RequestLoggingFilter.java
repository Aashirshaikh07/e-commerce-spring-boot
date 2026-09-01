package com.aashir.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();

        long startTime = System.currentTimeMillis();

        try {
            MDC.put(REQUEST_ID, requestId);

            response.setHeader("X-Request-Id", requestId);

            log.info(
                    "HTTP Request: method={} uri={}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);

        } finally {

            long duration =
                    System.currentTimeMillis() - startTime;

            int status = response.getStatus();

            if (status >= 500) {

                log.error(
                        "HTTP Response: method={} uri={} status={} duration={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        duration
                );

            } else if (status >= 400) {

                log.warn(
                        "HTTP Response: method={} uri={} status={} duration={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        duration
                );

            } else {

                log.info(
                        "HTTP Response: method={} uri={} status={} duration={}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        duration
                );
            }

            MDC.remove(REQUEST_ID);
        }
    }
}