package com.test.agentic.dto;

public record CodeReviewResult(
        String ruleName,
        String severity,
        String existingCode,
        String suggestedCode,
        String suggestedDescription
) {}
