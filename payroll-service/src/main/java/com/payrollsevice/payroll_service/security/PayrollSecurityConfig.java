package com.payrollsevice.payroll_service.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration(proxyBeanMethods = false)
public class PayrollSecurityConfig {
    @Bean
    OAuth2TokenValidator<Jwt> requireJwtExpiration() {
        return new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);
    }

    @Bean
    SecurityFilterChain financeSecurity(HttpSecurity http) {
        var paths = PathPatternRequestMatcher.withDefaults();
        return http
            .securityMatcher(new OrRequestMatcher(
                paths.matcher("/payroll/*/finance-report"), paths.matcher("/payroll/*/finance-report.csv"),
                paths.matcher("/payroll/*/calculate"), paths.matcher("/payroll/*/close"),
                paths.matcher(HttpMethod.GET, "/payslip"), paths.matcher(HttpMethod.GET, "/payslip/{id}"),
                paths.matcher(HttpMethod.HEAD, "/payslip"), paths.matcher(HttpMethod.HEAD, "/payslip/{id}"),
                paths.matcher(HttpMethod.GET, "/payslip/{id}/document"),
                paths.matcher(HttpMethod.HEAD, "/payslip/{id}/document")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("FINANCE"))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(financeRoleConverter())))
            .build();
    }

    private static JwtAuthenticationConverter financeRoleConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess
                    && realmAccess.get("roles") instanceof Collection<?> roles
                    && roles.contains("FINANCE")) {
                return List.of(new SimpleGrantedAuthority("ROLE_FINANCE"));
            }
            return List.of();
        });
        return converter;
    }
}
