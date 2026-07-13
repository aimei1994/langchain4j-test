package com.test.controller;

import com.test.agentic.AgenticTestService;
import com.test.agentic.dto.DynamicReviewRequest;
import com.test.agentic.dto.ReviewCodeModel;
import com.test.dispatcher.dto.AgentResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agentic")
public class AgenticReviewController {

    private static final Logger log = LoggerFactory.getLogger(AgenticReviewController.class);

    private final AgenticTestService agenticTestService;

    public AgenticReviewController(AgenticTestService agenticTestService) {
        this.agenticTestService = agenticTestService;
    }

    @PostMapping("/dynamic-review_2")
    public ResponseEntity<AgentResponse> dynamicReview2(@Valid @RequestBody DynamicReviewRequest request) {
        try {
            ReviewCodeModel result = agenticTestService.reviewWithSkills(request.skills(), request.code());
            return ResponseEntity.ok(AgentResponse.ok(result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(AgentResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error running dynamic skill review: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(AgentResponse.error(rootCauseMessage(e)));
        }
    }

    // AgentInvocationException's own message is just "Failed to invoke agent method: ..." —
    // the actual reason (bad API key, model refusal, JSON schema mismatch, tool failure, etc.)
    // is on the wrapped cause from the reflective invoke(), so surface that instead.
    private static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
