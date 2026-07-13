package com.test.agentic;

import com.test.agentic.dto.CodeReviewResults;
import com.test.agentic.dto.ReviewCodeModel;
import com.test.agentic.dto.ReviewFinding;
import com.test.config.StructuredOutputChatModel;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.SkillResource;
import dev.langchain4j.skills.Skills;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgenticTestService {
    private static final Logger log = LoggerFactory.getLogger(AgenticTestService.class);

    private final StructuredOutputChatModel structuredOutputChatModel;
    private final String skillsClasspath;

    // Shared across calls — a subAgent's LLM calls run on this pool for the lifetime of the
    // request; owned once per bean instead of being spun up (and never shut down) per call.
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private List<Skill> loadedSkills;

    public AgenticTestService(StructuredOutputChatModel structuredOutputChatModel,
                              @Value("${agent.skills.classpath}") String skillsClasspath) {
        this.structuredOutputChatModel = structuredOutputChatModel;
        this.skillsClasspath = skillsClasspath;
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

        log.info("AgenticTestService ready with {} skill(s): {}",
                loadedSkills.size(),
                loadedSkills.stream().map(Skill::name).toList()
        );
    }

    // Caller names the skills to run — one CodeReviewSubAgent is built per named skill, each
    // wired with ONLY that skill's own content/resources/toolProvider (never the full catalog),
    // so a subagent can never skip or swap out the skill it was asked to run. CodeReviewSupervisor
    // (AgenticServices.parallelBuilder) fans the same (language, code) input out to every
    // subAgent in parallel, and the supervisor's .output(...) function merges their structured
    // findings back into one ReviewCodeModel, attributing each finding to its source skill.
    public ReviewCodeModel reviewWithSkills(List<String> skillNames, String code) {
        validateSkillNames(skillNames);

        List<CodeReviewSubAgent> subAgents = skillNames.stream()
                .map(this::generateSubAgent)
                .toList();

        CodeReviewSupervisor supervisor = AgenticServices.parallelBuilder(CodeReviewSupervisor.class)
                .subAgents(subAgents)
                .executor(executor)
                .output(scope -> mergeFindings(scope, skillNames))
                .build();

        ReviewCodeModel aa = supervisor.review("java", code);
        return aa;
    }

    private ReviewCodeModel mergeFindings(AgenticScope scope, List<String> skillNames) {
        List<ReviewFinding> merged = new ArrayList<>();
        TokenUsage totalTokenUsage = null;
        List<ToolExecution> allToolExecutions = new ArrayList<>();
        for (String skillName : skillNames) {
            @SuppressWarnings("unchecked")
            Result<CodeReviewResults> subResult =
                    (Result<CodeReviewResults>) scope.readState(outputKeyFor(skillName));
            if (subResult == null || subResult.content() == null || subResult.content().findings() == null) {
                continue;
            }
            // Attribution is stamped here rather than trusted from the model's own output.
            subResult.content().findings().forEach(r -> merged.add(new ReviewFinding(
                    skillName, r.ruleName(), r.severity(),
                    r.existingCode(), r.suggestedCode(), r.suggestedDescription())));
            totalTokenUsage = TokenUsage.sum(totalTokenUsage, subResult.tokenUsage());
            allToolExecutions.addAll(subResult.toolExecutions());
        }
        int inputTokenCount = totalTokenUsage != null && totalTokenUsage.inputTokenCount() != null
                ? totalTokenUsage.inputTokenCount() : 0;
        int outputTokenCount = totalTokenUsage != null && totalTokenUsage.outputTokenCount() != null
                ? totalTokenUsage.outputTokenCount() : 0;
        List<String> toolNames = allToolExecutions.stream()
                .map(te -> te.request().name())
                .toList();
        return new ReviewCodeModel(merged, inputTokenCount, outputTokenCount, allToolExecutions.size(), toolNames);
    }

    private CodeReviewSubAgent generateSubAgent(String skillName) {
        Skill skill = findSkill(skillName);
        return AgenticServices.agentBuilder(CodeReviewSubAgent.class)
                .chatModel(structuredOutputChatModel)
                .name(skillName + "Agent")
                .outputKey(outputKeyFor(skillName))
                // systemMessageProvider (not systemMessage) — skill/resource content can contain
                // literal "{{...}}" (e.g. Jinja2 examples in rule.md), which .systemMessage(String)
                // would misparse as its own template variables.
                .systemMessageProvider(ctx -> skillSystemMessage(skill))
                // Scoped to this single skill's own toolProvider — the subagent has no way to see
                // or activate any other skill in the catalog.
                .toolProvider(Skills.from(skill).toolProvider())
                .build();
    }

    private String outputKeyFor(String skillName) {
        return skillName.replaceAll("[^a-zA-Z0-9]", "") + "Result";
    }

    private void validateSkillNames(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Expected at least one skill. Available skills: " + availableSkills());
        }
        Set<String> distinct = new LinkedHashSet<>(skillNames);
        if (distinct.size() != skillNames.size()) {
            throw new IllegalArgumentException("Skills must not contain duplicates: " + skillNames);
        }
        for (String skillName : skillNames) {
            findSkill(skillName); // throws IllegalStateException if unknown
        }
    }

    private List<String> availableSkills() {
        return loadedSkills.stream().map(Skill::name).sorted().toList();
    }

    private Skill findSkill(String name) {
        return loadedSkills.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Skill not found: " + name));
    }

    // Resource content (e.g. reference/rule.md) is embedded directly rather than left for the
    // model to fetch via the read_skill_resource tool. Relying on a two-hop tool call
    // (activate_skill, then read_skill_resource) to learn the actual rules is unreliable with
    // weaker models: observed behavior was the model skipping that step and inventing generic
    // OWASP-style findings that matched neither skill's real ruleName values. Embedding the rules
    // up front removes that failure mode entirely — this is the mechanism that stops the LLM from
    // skipping the skill.
    private String skillSystemMessage(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a specialized agent whose only job is to run the '")
                .append(skill.name()).append("' skill.\n\n")
                .append(sanitizeTemplateBraces(skill.content())).append("\n\n");

        for (SkillResource resource : skill.resources()) {
            sb.append("=== Resource: ").append(resource.relativePath()).append(" ===\n")
                    .append(sanitizeTemplateBraces(resource.content())).append("\n\n");
        }

        sb.append("CRITICAL: The resource(s) above are the ONLY source of truth for rules. " +
                "Only report findings whose `ruleName` and `severity` match an entry listed there, verbatim. " +
                "Do not invent rule names, do not fall back on general security knowledge " +
                "(e.g. generic \"SQL injection\"/\"XSS\"/\"hard-coded credentials\" categories), and do not apply " +
                "rules from any other skill. If the code has no violations of the listed rules, return an empty findings list.\n\n" +
                "You also have filesystem tools: readFile, listFiles, grepInDirectory, checkDirectory. " +
                "Use these to read project files when the skill requires file access.");
        return sb.toString();
    }

    // langchain4j's system/user message builder methods run the string through its own
    // Mustache-style template engine (variables denoted "{{ name }}") with no escape syntax — ANY
    // literal "{{...}}" in embedded skill/resource content (e.g. a Jinja2 example in a rule.md) is
    // parsed as a required template variable and throws "Value for the variable '...' is missing".
    // Break the pattern by inserting a zero-width space between the braces; visually identical to
    // the model, invisible to users.
    private String sanitizeTemplateBraces(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("{{", "{​{").replace("}}", "}​}");
    }
}
