package com.test.agentic;

import com.test.agentic.dto.ReviewCodeModel;
import dev.langchain4j.service.V;

// Built via AgenticServices.parallelBuilder(CodeReviewSupervisor.class) — not an LLM-driven
// supervisor. The planner just fans "review" out to every registered CodeReviewSubAgent in
// parallel with the same (language, code) args, then the builder's .output(...) function reads
// each subAgent's own outputKey out of the shared AgenticScope and merges them into one
// ReviewCodeModel. See AgenticTestService#reviewWithSkills.
//
// Not wrapped in Result<...> — the token/tool-execution totals that Result would carry are
// already surfaced as plain fields on ReviewCodeModel itself (see #mergeFindings).
public interface CodeReviewSupervisor {

    ReviewCodeModel review(@V("language") String language, @V("code") String code);

}
