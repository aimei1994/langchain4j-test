package com.test.review;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface PrReviewAiService {

    @SystemMessage("""
            You are a senior code reviewer. Review the given diff and return
            findings as a JSON array (ruleName, severity, reason, startLine, endLine).
            Retrieved past reviewer feedback, if any, is appended below the diff —
            do not repeat a finding a reviewer previously marked REJECTED for the
            same rule on similar code, and match the tone of notes left on ACCEPTED ones.
            """)
    String review(@MemoryId String prId, @UserMessage String diff);
}
