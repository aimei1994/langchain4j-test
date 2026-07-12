package com.test.agentic;

import com.test.agentic.dto.CodeReviewResult;
import com.test.agentic.dto.ReviewFinding;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface CodeReviewSubAgent {

    // Which skill this agent runs is fixed at build time (baked into its system prompt —
    // see AgenticTestService#skillSystemMessage), not passed at call time: when used as a
    // parallel subAgent under CodeReviewSupervisor, every subAgent receives the same
    // (language, code) arguments from the shared AgenticScope, so a per-call "skill" argument
    // could never actually vary between subAgent instances.
    @UserMessage("As a {{language}} developer, analyze and review the following 'N: ' prefixed code: {{code}}")
    @Agent(
            name = "codeReviewAgent",
            description = "Reviews source code for rule violations (via a single code-review skill " +
                    "baked into this agent's system prompt) and returns structured findings."
    )
    Result<List<CodeReviewResult>> review(@V("language") String language, @V("code") String code);
}
