package com.test.skill;

import dev.langchain4j.agent.tool.Tool;

import java.util.Map;

// Lets the model fetch one skill's own bundled reference files on demand
// (e.g. "reference/rule.md") instead of eagerly inlining all of them into
// the system message. Scoped to a single skill's resources — built once
// per skill in SkillService so there's no cross-skill leakage.
public class SkillResourceTool {

    private final Map<String, String> resourcesByPath;

    public SkillResourceTool(Map<String, String> resourcesByPath) {
        this.resourcesByPath = resourcesByPath;
    }

    @Tool("Read a reference file bundled with this skill (e.g. 'reference/rule.md'), " +
            "as instructed by the skill's own steps. Not for project source files — use readFile for those.")
    public String readSkillResource(String relativePath) {
        String content = resourcesByPath.get(relativePath);
        if (content == null) {
            return "ERROR: no such resource '" + relativePath + "'. Available: " + resourcesByPath.keySet();
        }
        return content;
    }
}
