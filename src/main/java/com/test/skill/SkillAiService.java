package com.test.skill;

import dev.langchain4j.service.UserMessage;

public interface SkillAiService {

    String chat(@UserMessage String userInput);
}
