package com.personnelservice.personnel_service.domain.jobPosition.dto;

import java.util.UUID;

import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobPositionDtos {
    public record Request(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
    ) {
    }

    public record Response(UUID id, String name, String description) {
        public static Response from(JobPosition entity) {
            return new Response(entity.getId(), entity.getName(), entity.getDescription());
        }
    }
}
