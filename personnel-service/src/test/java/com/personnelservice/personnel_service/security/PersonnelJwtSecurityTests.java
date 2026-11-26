package com.personnelservice.personnel_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.personnelservice.personnel_service.domain.employmentContract.dto.EmploymentContractDtos;
import com.personnelservice.personnel_service.domain.employmentContract.service.EmploymentContractService;
import com.personnelservice.personnel_service.domain.payrollInput.dto.PayrollInputResponse;
import com.personnelservice.personnel_service.domain.payrollInput.service.PayrollInputService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PersonnelJwtSecurityTests {
    private static final RSAKey TRUSTED_KEY = signingKey();
    private static final String ISSUER = "https://issuer.example.test/realms/payroll";
    private static final String AUDIENCE = "personnel-service";
    private static HttpServer jwksServer;

    @MockitoBean
    private PayrollInputService payrollInputs;

    @MockitoBean
    private EmploymentContractService contracts;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configureJwt(DynamicPropertyRegistry registry) throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] keys = new JWKSet(TRUSTED_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, keys.length);
            try (var output = exchange.getResponseBody()) {
                output.write(keys);
            }
        });
        jwksServer.start();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwks() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void rejectsTokensWithInvalidSignatureIssuerExpiryOrAudience() throws JOSEException {
        Instant now = Instant.now();
        String valid = token(TRUSTED_KEY, ISSUER, AUDIENCE, now.plusSeconds(300));
        String badSignature = token(signingKey(), ISSUER, AUDIENCE, now.plusSeconds(300));
        String wrongIssuer = token(TRUSTED_KEY, "https://other.example.test", AUDIENCE, now.plusSeconds(300));
        String wrongAudience = token(TRUSTED_KEY, ISSUER, "other-service", now.plusSeconds(300));
        String expired = token(TRUSTED_KEY, ISSUER, AUDIENCE, now.minusSeconds(120));
        String withoutExpiry = token(TRUSTED_KEY, ISSUER, AUDIENCE, null);

        var company = UUID.randomUUID();
        var contract = UUID.randomUUID();
        var period = YearMonth.of(2026, 11);
        when(payrollInputs.find(company, period)).thenReturn(new PayrollInputResponse(company, period, List.of()));
        when(contracts.findById(contract)).thenReturn(mock(EmploymentContractDtos.Response.class));
        String withoutPermission = token(TRUSTED_KEY, ISSUER, AUDIENCE, now.plusSeconds(300), "other.read");
        var routes = List.of("/internal/payroll-input?companyId=" + company + "&period=" + period,
            "/internal/employment-contract/" + contract + "/company");

        for (String route : routes) {
            assertThat(status(route, null)).isEqualTo(401);
            for (String invalid : new String[] {badSignature, wrongIssuer, wrongAudience, expired, withoutExpiry}) {
                assertThat(status(route, invalid)).isEqualTo(401);
            }
            assertThat(status(route, withoutPermission)).isEqualTo(403);
            assertThat(status(route, valid)).isEqualTo(200);
        }
    }

    private int status(String path, String token) {
        var request = RestClient.create("http://127.0.0.1:" + port).get().uri(path);
        if (token != null) {
            request.headers(headers -> headers.setBearerAuth(token));
        }
        return request.exchange((sent, response) -> response.getStatusCode().value());
    }

    private static RSAKey signingKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("personnel-test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not generate a test signing key.", exception);
        }
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expires) throws JOSEException {
        return token(key, issuer, audience, expires, "personnel.payroll.read");
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expires, String scope)
            throws JOSEException {
        var claims = new JWTClaimsSet.Builder().subject("payroll-service").issuer(issuer).audience(audience)
            .claim("scope", scope);
        if (expires != null) {
            claims.expirationTime(Date.from(expires));
        }

        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
