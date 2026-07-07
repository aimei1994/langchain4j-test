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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    // Overload of invoke() above for N skill names — runs each named skill
    // concurrently (one LLM round trip per skill, on its own virtual thread),
    // each still getting only that one skill's own system message and scoped
    // resource tool (see buildAiServiceFor). Skill bodies are never combined
    // into a single call — N skills means N independent calls, just run in
    // parallel instead of one after another so wall-clock time doesn't scale
    // linearly with skill count. One skill failing doesn't fail the rest.
    public Map<String, String> invoke(List<String> skillNames, String args) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<String>> futures = new LinkedHashMap<>();
            for (String skillName : skillNames) {
                futures.put(skillName, executor.submit(() -> invoke(skillName, args)));
            }

            Map<String, String> results = new LinkedHashMap<>();
            for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
                try {
                    results.put(entry.getKey(), entry.getValue().get());
                } catch (ExecutionException e) {
                    log.warn("Skill [{}] failed: {}", entry.getKey(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    results.put(entry.getKey(), "ERROR: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.put(entry.getKey(), "ERROR: interrupted");
                }
            }
            return results;
        }
    }

    // Returns list of loaded skill names
    public List<String> availableSkills() {
        return loadedSkills.stream()
                .map(Skill::name)
                .sorted()
                .toList();
    }

    // Used by AgentDispatcher to validate /mentions found in free text against
    // real skills, so only a name that actually exists ever gets dispatched.
    public boolean hasSkill(String skillName) {
        return skillsByName.containsKey(skillName);
    }
}
