package com.test.agentic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DynamicReviewRequest(
        @NotEmpty(message = "at least one skill must be provided")
        List<@NotBlank String> skills,

        @NotBlank(message = "code must not be blank")
        String code
) {}
