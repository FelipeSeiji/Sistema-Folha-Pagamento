package com.personnelservice.personnel_service.security;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class PersonnelSecurityConfig {
    @Bean
    OAuth2TokenValidator<Jwt> requireJwtExpiration() {
        return new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);
    }

    @Bean
    SecurityFilterChain personnelSecurity(HttpSecurity http) {
        return http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/internal/payroll-input", "/internal/employment-contract/{id}/company")
                .hasAuthority("SCOPE_personnel.payroll.read")
                .requestMatchers("/employment-contract", "/employment-contract/**", "/salary-history", "/salary-history/**")
                .hasRole("HR")
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(personnelAuthorities())))
            .build();
    }

    private static JwtAuthenticationConverter personnelAuthorities() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = scopes.convert(jwt);
            if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess
                    && realmAccess.get("roles") instanceof Collection<?> roles
                    && roles.contains("HR")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_HR"));
            }
            return authorities;
        });
        return converter;
    }
}
