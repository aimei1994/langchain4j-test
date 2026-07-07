package com.test.dispatcher;

import com.test.skill.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AgentDispatcher.class);

    // Matches /token mentions anywhere in free text, e.g. "use /code-review and
    // /connection-resilience-review". Only tokens that match a real, loaded
    // skill name are ever accepted — see extractKnownSkillMentions().
    private static final Pattern SKILL_MENTION = Pattern.compile("/([a-zA-Z][\\w-]*)");

    // A fenced code block, if the caller wrapped the payload in one.
    private static final Pattern FENCED_CODE_BLOCK =
            Pattern.compile("```[a-zA-Z0-9_+-]*\\r?\\n([\\s\\S]*?)```");

    private final SkillService skillService;

    public AgentDispatcher(SkillService skillService) {
        this.skillService = skillService;
    }

    // Returns String for a single skill, or Map<String,String> keyed by skill
    // name for more than one. Two input styles are supported:
    //   1. /skillname <args>            or  /skillA,skillB <args>
    //   2. free text mentioning /skillA and/or /skillB, with the code/payload
    //      either fenced in ``` ``` or following the first ':'
    // Either way, which skill(s) run is decided here by matching literal
    // /mentions against skillService.hasSkill() — never by asking the model.
    public Object handle(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "Input is empty. Use /skillname <input>. " +
                    "Available: " + skillService.availableSkills();
        }

        String trimmed = userInput.trim();

        return trimmed.startsWith("/")
                ? handleSlashPrefixed(trimmed)
                : handleFreeText(trimmed);
    }

    private Object handleSlashPrefixed(String trimmed) {
        String[] parts = trimmed.substring(1).split(" ", 2);
        String args = parts.length > 1 ? parts[1].trim() : "";

        List<String> skillNames = Arrays.stream(parts[0].split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(name -> !name.isEmpty())
                .toList();

        return dispatch(skillNames, args);
    }

    private Object handleFreeText(String text) {
        // Split off the payload first, so skill-mention scanning never looks
        // inside the code itself (avoids a stray "/foo" in the code being
        // mistaken for a skill reference).
        String instructionText;
        String args;

        Matcher fenced = FENCED_CODE_BLOCK.matcher(text);
        if (fenced.find()) {
            instructionText = text.substring(0, fenced.start());
            args = fenced.group(1).trim();
        } else {
            int colonIndex = text.indexOf(':');
            if (colonIndex >= 0 && colonIndex < text.length() - 1) {
                instructionText = text.substring(0, colonIndex);
                args = text.substring(colonIndex + 1).trim();
            } else {
                instructionText = text;
                args = text;
            }
        }

        List<String> skillNames = extractKnownSkillMentions(instructionText);
        if (skillNames.isEmpty()) {
            return "No known /skillname mentioned. Available: " +
                    skillService.availableSkills().stream().map(s -> "/" + s).toList();
        }

        return dispatch(skillNames, args);
    }

    private List<String> extractKnownSkillMentions(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = SKILL_MENTION.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).toLowerCase();
            if (skillService.hasSkill(candidate) && !found.contains(candidate)) {
                found.add(candidate);
            }
        }
        return found;
    }

    private Object dispatch(List<String> skillNames, String args) {
        if (skillNames.size() == 1) {
            String skillName = skillNames.get(0);
            log.info("Dispatching /{} with args [{}]", skillName, args);
            return skillService.invoke(skillName, args);
        }

        log.info("Dispatching {} with args [{}]", skillNames, args);
        return skillService.invoke(skillNames, args);
    }

    public String listAvailable() {
        return "Available skills: " + skillService.availableSkills()
                .stream()
                .map(s -> "/" + s)
                .toList();
    }
}
