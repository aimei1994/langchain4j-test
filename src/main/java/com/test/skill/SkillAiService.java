package com.test.skill;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SkillAiService {

    // skillInstructions is the exact SKILL.md body chosen deterministically in
    // SkillService.invoke() — not a tool the model can choose to skip or misname.
    @SystemMessage("{{skillInstructions}}")
    String chat(@V("skillInstructions") String skillInstructions, @UserMessage String userInput);
}
