package com.test.agentic;

import com.test.agentic.dto.ReviewCodeModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.V;

// Built via AgenticServices.parallelBuilder(CodeReviewSupervisor.class) — not an LLM-driven
// supervisor. The planner just fans "review" out to every registered CodeReviewSubAgent in
// parallel with the same (language, code) args, then the builder's .output(...) function reads
// each subAgent's own outputKey out of the shared AgenticScope and merges them into one
// ReviewCodeModel. See AgenticTestService#reviewWithSkills.
public interface CodeReviewSupervisor {

    Result<ReviewCodeModel> review(@V("language") String language, @V("code") String code);

}
