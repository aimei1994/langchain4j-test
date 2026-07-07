package com.test.review;

// One reviewer decision on one AI-generated comment.
public record ReviewFeedback(
        String prId,
        String filePath,
        int lineNumber,
        String ruleName,
        String aiComment,
        FeedbackDecision decision,
        String reviewerNote
) {
}
