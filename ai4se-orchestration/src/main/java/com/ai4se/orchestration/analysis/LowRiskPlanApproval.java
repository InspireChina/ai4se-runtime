package com.ai4se.orchestration.analysis;

import com.ai4se.orchestration.verification.VerificationEntries;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 窄范围低风险自动批准规则（人闸策略，不是取消 Approval）。
 * <ul>
 *   <li>Plan Allowed ⊆ 开跑 hint（可收紧，不可扩大）</li>
 *   <li>验证命令 ∈ entries.yaml（若提供）</li>
 *   <li>可选：Allowed 仅测试路径（{@code /test/} 或 {@code *Test.java}）</li>
 * </ul>
 */
public final class LowRiskPlanApproval {

    public static final String MODE = "low_risk_auto";
    public static final String APPROVER = "control-low-risk";

    private LowRiskPlanApproval() {
    }

    public static boolean isEligible(
            Path workspace,
            String storyId,
            List<String> allowedHint,
            String verifyCommand,
            boolean requireTestPathsOnly) throws IOException {
        return ineligibleReason(
                workspace, storyId, allowedHint, verifyCommand, requireTestPathsOnly) == null;
    }

    public static String ineligibleReason(
            Path workspace,
            String storyId,
            List<String> allowedHint,
            String verifyCommand,
            boolean requireTestPathsOnly) throws IOException {
        if (allowedHint == null || allowedHint.isEmpty()) {
            return "Allowed hint 为空，无法做低风险自动批准";
        }
        List<String> planAllowed = PlanRecords.readAllowedFiles(workspace, storyId);
        if (planAllowed.isEmpty()) {
            return "Plan Allowed 为空";
        }
        Set<String> hint = normalizeSet(allowedHint);
        List<String> outside = new ArrayList<String>();
        List<String> nonTest = new ArrayList<String>();
        for (String path : planAllowed) {
            String n = normalize(path);
            if (!hint.contains(n)) {
                outside.add(n);
            }
            if (requireTestPathsOnly && !isNarrowTestPath(n)) {
                nonTest.add(n);
            }
        }
        if (!outside.isEmpty()) {
            return "Plan Allowed 超出 hint: " + outside;
        }
        if (!nonTest.isEmpty()) {
            return "Plan Allowed 含非测试路径（窄范围策略）: " + nonTest;
        }
        if (!Strings.isBlank(verifyCommand)) {
            try {
                VerificationEntries.requireAllowedCommand(workspace, verifyCommand.trim());
            } catch (StageGateException e) {
                return "验证命令不在 entries: " + verifyCommand;
            }
        }
        return null;
    }

    static boolean isNarrowTestPath(String path) {
        String p = normalize(path).toLowerCase(Locale.ROOT);
        return p.contains("/test/") || p.contains("\\test\\") || p.endsWith("test.java");
    }

    private static Set<String> normalizeSet(List<String> paths) {
        Set<String> out = new LinkedHashSet<String>();
        for (String p : paths) {
            if (!Strings.isBlank(p)) {
                out.add(normalize(p));
            }
        }
        return out;
    }

    private static String normalize(String path) {
        return path.trim().replace('\\', '/');
    }
}
