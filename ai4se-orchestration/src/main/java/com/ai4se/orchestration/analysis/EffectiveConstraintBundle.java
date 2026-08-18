package com.ai4se.orchestration.analysis;

import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.context.rules.CustomerRuleLoader;
import com.ai4se.context.rules.RuleDocument;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.support.WorkspaceGit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic, immutable per-Story constraint record compiled after Planning and before human
 * approval. It is deliberately a small properties/Markdown pair, not a second rule engine.
 */
public final class EffectiveConstraintBundle {

    public static final String PROPERTIES_FILE = "effective-constraints.properties";
    public static final String MARKDOWN_FILE = "effective-constraints.md";

    private EffectiveConstraintBundle() {
    }

    public static Path propertiesPath(Path workspace, String storyId) {
        return PlanRecords.planningDir(workspace, storyId).resolve(PROPERTIES_FILE);
    }

    public static Path markdownPath(Path workspace, String storyId) {
        return PlanRecords.planningDir(workspace, storyId).resolve(MARKDOWN_FILE);
    }

    /** Create once. A later Plan/rule change must create a new Story/approval instead of mutating it. */
    public static void compileIfAbsent(Path workspace, String storyId, ProcessInvoker invoker)
            throws IOException {
        if (Files.isRegularFile(propertiesPath(workspace, storyId))
                && Files.isRegularFile(markdownPath(workspace, storyId))) {
            return;
        }
        PlanRecords.requireFormalPlanWithAllowed(workspace, storyId);
        String baseline = WorkspaceGit.headSha(workspace, invoker);
        Path plan = PlanRecords.planningDir(workspace, storyId).resolve(PlanRecords.PLAN_FILE);
        byte[] planBytes = Files.readAllBytes(plan);
        List<String> allowed = PlanRecords.readAllowedFiles(workspace, storyId);
        List<RuleDocument> rules = new ArrayList<RuleDocument>();
        for (RuleDocument rule : CustomerRuleLoader.loadAll(workspace)) {
            if (rule.appliesToRole("Development") || rule.appliesToRole("Review")) {
                rules.add(rule);
            }
        }

        StringBuilder properties = new StringBuilder();
        properties.append("baseline_commit=").append(baseline).append('\n');
        properties.append("plan_sha256=").append(ModelInputEnvelope.sha256(planBytes)).append('\n');
        properties.append("allowed_count=").append(allowed.size()).append('\n');
        properties.append("rule_count=").append(rules.size()).append('\n');
        for (int i = 0; i < allowed.size(); i++) {
            properties.append("allowed.").append(i + 1).append('=').append(allowed.get(i)).append('\n');
        }
        for (int i = 0; i < rules.size(); i++) {
            RuleDocument rule = rules.get(i);
            String source = rule.source() == null ? "" : rule.source().path;
            byte[] sourceBytes = Files.readAllBytes(java.nio.file.Paths.get(source));
            properties.append("rule.").append(i + 1).append(".id=").append(rule.id()).append('\n');
            properties.append("rule.").append(i + 1).append(".sha256=")
                    .append(ModelInputEnvelope.sha256(sourceBytes)).append('\n');
            properties.append("rule.").append(i + 1).append(".source=").append(source).append('\n');
        }
        Path dir = PlanRecords.planningDir(workspace, storyId);
        Files.createDirectories(dir);
        Files.write(propertiesPath(workspace, storyId),
                properties.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder markdown = new StringBuilder("# Effective Constraint Bundle\n\n");
        markdown.append("- baseline_commit: ").append(baseline).append('\n');
        markdown.append("- plan_sha256: ").append(ModelInputEnvelope.sha256(planBytes)).append('\n');
        markdown.append("- immutable_after_approval: true\n\n");
        markdown.append("## Allowed Files\n\n");
        for (String path : allowed) {
            markdown.append("- ").append(path).append('\n');
        }
        markdown.append("\n## Applicable Rules\n\n");
        if (rules.isEmpty()) {
            markdown.append("- (no customer rule matched Development or Review)\n");
        } else {
            for (RuleDocument rule : rules) {
                markdown.append("### ").append(rule.id()).append("\n\n")
                        .append(rule.body()).append('\n');
            }
        }
        markdown.append("\n## Mandatory behavior\n\n")
                .append("- Preserve the frozen Acceptance and Allowed Files.\n")
                .append("- Do not change rules, probes, requirement, plan or this bundle during Development.\n")
                .append("- A Defect is incremental evidence; it never replaces these constraints.\n");
        Files.write(markdownPath(workspace, storyId), markdown.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void requirePresent(Path workspace, String storyId) {
        if (!Files.isRegularFile(propertiesPath(workspace, storyId))
                || !Files.isRegularFile(markdownPath(workspace, storyId))) {
            throw new StageGateException(
                    "Effective Constraint Bundle missing — compile it after Plan and before Development");
        }
    }

    public static List<String> readAllowedOrEmpty(Path workspace, String storyId) throws IOException {
        if (!Files.isRegularFile(propertiesPath(workspace, storyId))) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String line : Files.readAllLines(propertiesPath(workspace, storyId), StandardCharsets.UTF_8)) {
            if (line.startsWith("allowed.")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    out.add(line.substring(eq + 1).trim());
                }
            }
        }
        return out;
    }
}
