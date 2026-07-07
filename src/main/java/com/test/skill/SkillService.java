package com.test.skill;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.SkillResource;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final ChatModel chatModel;
    private final String skillsClasspath;
    private final FileSystemTools fileSystemTools;

    private List<Skill> loadedSkills;
    private Map<String, Skill> skillsByName;
    private Map<String, SkillAiService> aiServiceBySkill;

    public SkillService(ChatModel chatModel,
                        @Value("${agent.skills.classpath}") String skillsClasspath,
                        FileSystemTools fileSystemTools) {
        this.chatModel       = chatModel;
        this.skillsClasspath = skillsClasspath;
        this.fileSystemTools = fileSystemTools;
    }

    @PostConstruct
    public void init() {
        // Use official ClassPathSkillLoader — reads from src/main/resources/skills/
        // Each subdirectory with a SKILL.md is loaded automatically
        loadedSkills = ClassPathSkillLoader.loadSkills(skillsClasspath);

        if (loadedSkills.isEmpty()) {
            throw new IllegalStateException(
                    "No skills found at classpath: " + skillsClasspath
            );
        }

        skillsByName = loadedSkills.stream()
                .collect(Collectors.toMap(Skill::name, Function.identity()));

        // No activate_skill tool here on purpose: which skill runs is decided in
        // invoke() below from the /skillname the caller already gave us, not by
        // letting the model pick a tool. Each skill gets its own AiService,
        // scoped to only its own reference resources — that's what lets the
        // model still fulfil steps like "Read reference/rule.md" without either
        // eagerly inlining every skill's (sometimes large) reference material
        // or leaking one skill's resources into another's tool set.
        aiServiceBySkill = loadedSkills.stream()
                .collect(Collectors.toMap(Skill::name, this::buildAiServiceFor));

        loadedSkills.forEach(s ->
                log.info("Loaded skill: [{}] — {} ({} resource(s))",
                        s.name(), s.description(), s.resources().size())
        );

        log.info("SkillService ready with {} skill(s): {}",
                loadedSkills.size(),
                loadedSkills.stream().map(Skill::name).toList()
        );
    }

    private SkillAiService buildAiServiceFor(Skill skill) {
        Map<String, String> resourcesByPath = skill.resources().stream()
                .collect(Collectors.toMap(SkillResource::relativePath, SkillResource::content));

        return AiServices.builder(SkillAiService.class)
                .chatModel(chatModel)
                .tools(fileSystemTools, new SkillResourceTool(resourcesByPath))
                .build();
    }

    // Dispatches /skillname <args> — deterministically loads that skill's own
    // instructions as the system message, instead of asking the model to pick.
    public String invoke(String skillName, String args) {
        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            return "Unknown skill: /" + skillName + ". Available: " + availableSkills();
        }

        log.info("Invoking skill [{}] with args: [{}]", skillName, args);
        return aiServiceBySkill.get(skillName).chat(skill.content(), args);
    }

    // Returns list of loaded skill names
    public List<String> availableSkills() {
        return loadedSkills.stream()
                .map(Skill::name)
                .sorted()
                .toList();
    }
}
