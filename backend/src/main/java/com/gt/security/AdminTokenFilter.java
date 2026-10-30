package com.gt.security;

import com.gt.config.Gt2Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AdminTokenFilter extends OncePerRequestFilter {
    private final Gt2Properties properties;
    private final SecretHashing hashing;

    public AdminTokenFilter(Gt2Properties properties, SecretHashing hashing) {
        this.properties = properties;
        this.hashing = hashing;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String candidate = request.getHeader("X-GT2-Admin-Token");
        if (candidate != null && hashing.constantTimeEquals(candidate, properties.adminToken())) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "gt2-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
        chain.doFilter(request, response);
    }
}
