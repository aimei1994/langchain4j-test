package com.test.agentic.dto;

public record ReviewFinding(
        String skill,
        String ruleName,
        String severity,
        String existingCode,
        String suggestedCode,
        String suggestedDescription
) {}
