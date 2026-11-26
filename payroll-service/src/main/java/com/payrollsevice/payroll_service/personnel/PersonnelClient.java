package com.payrollsevice.payroll_service.personnel;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;

@Component
public class PersonnelClient {
    private final RestClient client;

    public PersonnelClient(RestClient.Builder builder, @Value("${personnel.base-url}") String baseUrl,
                           ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        var oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        oauth.setClientRegistrationIdResolver(request -> "personnel");
        oauth.setPrincipalResolver(request -> new AnonymousAuthenticationToken(
            "payroll-service", "payroll-service", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        oauth.setAuthorizationFailureHandler(OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(clients));
        this.client = builder.baseUrl(baseUrl).requestInterceptor(oauth).build();
    }

    public PersonnelPayrollInput getPayrollInputs(UUID companyId, YearMonth period) {
        PersonnelPayrollInput input;
        try {
            input = client.get()
                .uri("/internal/payroll-input?companyId={companyId}&period={period}", companyId, period)
                .retrieve()
                .body(PersonnelPayrollInput.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new PersonnelInputConflictException(companyId, period);
            }
            throw new PersonnelUnavailableException("Personnel returned HTTP " + exception.getStatusCode().value(),
                exception);
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            throw new PersonnelUnavailableException("Could not retrieve payroll inputs from personnel.", exception);
        }

        if (input == null || !companyId.equals(input.companyId()) || !period.equals(input.period())
                || input.contracts() == null) {
            throw new PersonnelUnavailableException("Personnel returned invalid payroll inputs.", null);
        }
        return input;
    }

    public Optional<UUID> findContractCompanyId(UUID contractId) {
        ContractCompany contract;
        try {
            contract = client.get().uri("/internal/employment-contract/{id}/company", contractId)
                .retrieve().body(ContractCompany.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new PersonnelUnavailableException("Could not retrieve contract company from personnel.", exception);
        } catch (RestClientException | OAuth2AuthorizationException exception) {
            throw new PersonnelUnavailableException("Could not retrieve contract company from personnel.", exception);
        }

        if (contract == null || !contractId.equals(contract.id()) || contract.companyId() == null) {
            throw new PersonnelUnavailableException("Personnel returned an invalid contract company.", null);
        }
        return Optional.of(contract.companyId());
    }

    private record ContractCompany(UUID id, UUID companyId) {
    }
}
