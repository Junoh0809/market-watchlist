package com.stockmanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth,
                                            AuthenticationEntryPoint entryPoint) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new BearerAuthFilter(auth, entryPoint), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), new ApiExceptionHandler.ErrorResponse(
                    new ApiExceptionHandler.Error("UNAUTHENTICATED", "Invalid or missing session",
                            "req-" + UUID.randomUUID())));
        };
    }

    static class BearerAuthFilter extends OncePerRequestFilter {
        private final AuthService auth;
        private final AuthenticationEntryPoint entryPoint;

        BearerAuthFilter(AuthService auth, AuthenticationEntryPoint entryPoint) {
            this.auth = auth;
            this.entryPoint = entryPoint;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            if (authorization != null) {
                try {
                    String userId = auth.userId(authorization);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
                } catch (ResponseStatusException exception) {
                    SecurityContextHolder.clearContext();
                    entryPoint.commence(request, response, null);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
