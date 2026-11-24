package com.personnelservice.personnel_service.domain.employee.dto;

import java.util.UUID;

import com.personnelservice.personnel_service.domain.employee.model.Employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmployeeDtos {
    public record Request(
        @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) String email
    ) {
    }

    public record Response(UUID id, String cpf, String name, String email) {
        public static Response from(Employee employee) {
            return new Response(employee.getId(), employee.getCpf(), employee.getName(), employee.getEmail());
        }
    }
}
