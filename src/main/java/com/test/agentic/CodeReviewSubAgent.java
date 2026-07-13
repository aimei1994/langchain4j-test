package com.test.agentic;

import com.test.agentic.dto.CodeReviewResults;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CodeReviewSubAgent {

    // Which skill this agent runs is fixed at build time (baked into its system prompt —
    // see AgenticTestService#skillSystemMessage), not passed at call time: when used as a
    // parallel subAgent under CodeReviewSupervisor, every subAgent receives the same
    // (language, code) arguments from the shared AgenticScope, so a per-call "skill" argument
    // could never actually vary between subAgent instances.
    //
    // Returns a wrapper POJO (CodeReviewResults), not a raw List<CodeReviewResult>: langchain4j's
    // structured-output parser for a top-level List<Pojo> expects the model to emit an
    // OpenAI-style wrapped array (`{"values": [...]}`) and only checks for a leading '{' to
    // detect it. Some OpenAI-compatible gateways ignore that convention and return a bare
    // `[...]` array instead, which then gets misparsed as a single Pojo (Jackson: "Cannot
    // deserialize value of type CodeReviewResult from Array value"). An object-typed return
    // goes through PojoOutputParser instead, which has no such wrapper assumption.
    @UserMessage("As a {{language}} developer, analyze and review the following 'N: ' prefixed code: {{code}}")
    @Agent(
            name = "codeReviewAgent",
            description = "Reviews source code for rule violations (via a single code-review skill " +
                    "baked into this agent's system prompt) and returns structured findings."
    )
    Result<CodeReviewResults> review(@V("language") String language, @V("code") String code);
}
