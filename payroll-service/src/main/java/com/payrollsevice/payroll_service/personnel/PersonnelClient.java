package com.payrollsevice.payroll_service.personnel;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;

@Component
public class PersonnelClient {
    private final RestClient client;

    public PersonnelClient(RestClient.Builder builder, @Value("${personnel.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
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
        } catch (RestClientException exception) {
            throw new PersonnelUnavailableException("Could not retrieve payroll inputs from personnel.", exception);
        }

        if (input == null || !companyId.equals(input.companyId()) || !period.equals(input.period())
                || input.contracts() == null) {
            throw new PersonnelUnavailableException("Personnel returned invalid payroll inputs.", null);
        }
        return input;
    }
}
