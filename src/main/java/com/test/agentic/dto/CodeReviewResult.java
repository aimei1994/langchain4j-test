package com.test.agentic.dto;

import dev.langchain4j.model.output.structured.Description;

public record CodeReviewResult(
        @Description("Exact ruleName of the violated rule, verbatim from the skill's rule resource. Never invented.")
        String ruleName,
        @Description("Severity of the violation, verbatim as defined for this rule in the skill's rule resource (e.g. critical, high, medium, low).")
        String severity,
        @Description("The offending code, prefixed with its 'N: ' line number exactly as it appeared in the reviewed source.")
        String existingCode,
        @Description("The corrected replacement code, prefixed with the same 'N: ' line number as existingCode.")
        String suggestedCode,
        @Description("Short human-readable explanation of why existingCode violates the rule and how suggestedCode fixes it.")
        String suggestedDescription
) {}
