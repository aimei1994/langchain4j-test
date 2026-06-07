package com.test.skill;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final ChatModel chatModel;
    private final String skillsClasspath;
    private final FileSystemTools fileSystemTools;

    private Skills skills;
    private List<Skill> loadedSkills;
    private SkillAiService aiService;

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

        loadedSkills.forEach(s ->
                log.info("Loaded skill: [{}] — {}", s.name(), s.description())
        );

        // Wrap into Skills — provides toolProvider + formatAvailableSkills()
        skills = Skills.from(loadedSkills);

        // Build AiService wired with Skills tool provider + filesystem tools
        aiService = AiServices.builder(SkillAiService.class)
                .chatModel(chatModel)
                .toolProvider(skills.toolProvider())
                .tools(fileSystemTools)
                .systemMessageProvider(memoryId ->
                        "You have access to the following skills:\n" +
                                skills.formatAvailableSkills() +
                                "\nWhen the user's request relates to one of these skills, " +
                                "activate it first using the `activate_skill` tool before proceeding.\n" +
                                "You also have filesystem tools: readFile, listFiles, grepInDirectory, checkDirectory. " +
                                "Use these to read project files when a skill requires file access."
                )
                .build();

        log.info("SkillService ready with {} skill(s): {}",
                loadedSkills.size(),
                loadedSkills.stream().map(Skill::name).toList()
        );
    }

    // Dispatches /skillname <args> — extracts skill name and routes to aiService
    public String invoke(String skillName, String args) {
        // The LLM will activate the right skill via activate_skill tool
        String prompt = "Use the /" + skillName + " skill for: " + args;
        log.info("Invoking skill [{}] with args: [{}]", skillName, args);
        return aiService.chat(prompt);
    }

    // Returns list of loaded skill names
    public List<String> availableSkills() {
        return loadedSkills.stream()
                .map(Skill::name)
                .sorted()
                .toList();
    }
}
