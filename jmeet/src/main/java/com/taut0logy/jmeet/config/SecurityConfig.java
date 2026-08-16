package com.taut0logy.jmeet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler oauth2SuccessHandler,
            ObjectMapper objectMapper) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/api/internal/livekit/webhook"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/verify-email/resend",
                                "/api/auth/login",
                                "/api/auth/request-password-reset",
                                "/api/auth/reset-password",
                                "/api/internal/livekit/webhook",
                                "/api/files/**",
                                "/api/meetings/by-code/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2.successHandler(oauth2SuccessHandler))
                .sessionManagement(session -> session.maximumSessions(-1))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        (request, response, authException) -> {
                            response.setStatus(ErrorCode.AUTH_REQUIRED.status().value());
                            response.setContentType("application/json");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    new ErrorResponse("Authentication required.", ErrorCode.AUTH_REQUIRED.name())));
                        },
                        request -> request.getServletPath().startsWith("/api")));

        return http.build();
    }
}
