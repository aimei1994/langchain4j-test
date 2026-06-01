package com.test.dispatcher;

import com.test.skill.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AgentDispatcher.class);

    private final SkillService skillService;

    public AgentDispatcher(SkillService skillService) {
        this.skillService = skillService;
    }


    public String handle(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "Input is empty. Use /skillname <input>. " +
                    "Available: " + skillService.availableSkills();
        }
/*
        if (!userInput.trim().startsWith("/")) {
            return "Please invoke a skill using /skillname <input>. " +
                    "Available: " + skillService.availableSkills();
        }*/

        String[] parts   = userInput.trim().substring(1).split(" ", 2);
        String skillName = parts[0].toLowerCase();
        String args      = parts.length > 1 ? parts[1].trim() : "";

        log.info("Dispatching /{} with args [{}]", skillName, args);

        return skillService.invoke(skillName, args);
    }

    public String listAvailable() {
        return "Available skills: " + skillService.availableSkills()
                .stream()
                .map(s -> "/" + s)
                .toList();
    }
}
