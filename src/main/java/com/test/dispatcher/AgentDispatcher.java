package com.test.dispatcher;

import com.test.skill.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AgentDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AgentDispatcher.class);

    private final SkillService skillService;

    public AgentDispatcher(SkillService skillService) {
        this.skillService = skillService;
    }


    // Returns String for a single /skillname call, or Map<String,String> keyed
    // by skill name for a comma-separated /skillA,skillB call.
    public Object handle(String userInput) {
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
        String args      = parts.length > 1 ? parts[1].trim() : "";

        List<String> skillNames = Arrays.stream(parts[0].split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(name -> !name.isEmpty())
                .toList();

        if (skillNames.size() == 1) {
            String skillName = skillNames.get(0);
            log.info("Dispatching /{} with args [{}]", skillName, args);
            return skillService.invoke(skillName, args);
        }

        log.info("Dispatching {} with args [{}]", skillNames, args);
        return skillService.invokeAll(skillNames, args);
    }

    public String listAvailable() {
        return "Available skills: " + skillService.availableSkills()
                .stream()
                .map(s -> "/" + s)
                .toList();
    }
}
