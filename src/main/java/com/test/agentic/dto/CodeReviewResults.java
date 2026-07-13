package com.test.agentic.dto;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record CodeReviewResults(
        @Description("All rule violations found in the reviewed code for this skill. Empty list if none found.")
        List<CodeReviewResult> findings
) {}
