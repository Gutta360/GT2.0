package com.gt.config;

import tools.jackson.databind.ObjectMapper;
import com.gt.security.AdminTokenFilter;
import com.gt.security.DeviceAuthenticationService;
import com.gt.security.DeviceTokenFilter;
import com.gt.security.SecretHashing;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Gt2Properties properties,
                                            SecretHashing hashing, DeviceAuthenticationService devices,
                                            ObjectMapper objectMapper) throws Exception {
        AdminTokenFilter adminFilter = new AdminTokenFilter(properties, hashing);
        DeviceTokenFilter deviceFilter = new DeviceTokenFilter(devices);
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/devices/register").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("DEVICE")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of("UNAUTHENTICATED", "Authentication is required."));
                }).accessDeniedHandler((request, response, error) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of("FORBIDDEN", "This credential is not authorized."));
                }))
                .addFilterBefore(adminFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(deviceFilter, AdminTokenFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(Gt2Properties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-GT2-Admin-Token", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Snapshot-Sha256", "X-Snapshot-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
