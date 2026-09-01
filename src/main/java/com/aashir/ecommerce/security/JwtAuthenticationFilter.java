package com.aashir.ecommerce.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomerUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomerUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

         if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
            }

        String jwt = authHeader.substring(7);

         try {
             String username = jwtService.extractUsername(jwt);

             if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                 UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                 boolean valid = jwtService.isTokenValid(jwt, userDetails);


                 if (valid) {

                     UsernamePasswordAuthenticationToken authToken =
                             new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                     authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                     SecurityContextHolder.getContext().setAuthentication(authToken);
                 }
             }
         }catch (ExpiredJwtException exception) {
             log.warn(
                     "JWT expired:method={} uri={}",
                     request.getMethod(),
                     request.getRequestURI()
             );
             response.setStatus(
                     HttpServletResponse.SC_UNAUTHORIZED
             );
             response.getWriter().write(
                     "{\"status\":401,\"message\":\"JWT token has expired\"}"
             );

             return;
         }catch (JwtException exception) {
             log.warn(
                     "Invalid JWT: method={} uri={}",
                     request.getMethod(),
                     request.getRequestURI()
             );

             response.setStatus(
                     HttpServletResponse.SC_UNAUTHORIZED
             );

             response.setContentType("application/json");

             response.getWriter().write(
                     "{\"status\":401,\"message\":\"Invalid JWT token\"}"
             );

             return;
         }

        filterChain.doFilter(request,response);
    }
}
