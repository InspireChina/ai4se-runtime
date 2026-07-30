package com.ai4se.runtime.demo.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Wave B/C: Human Answer → Gap Recheck → continue or stay blocked.
 * Does not claim Execution.
 */
class ClarificationResumePathTest {

    @Test
    void concreteGreenfieldAnswers_gapRecheck_allowsPlanning() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace-promotion");
        File out = new File(module, "target/clarification-resume-bundle-test");

        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                out.toPath(),
                RequirementAnalysisPipeline.promotionConcreteGreenfieldAnswers(),
                true,
                "clarification-resume");

        assertTrue(result.gap.mayPlan(), "concrete answers must unlock planning: " + result.gap.getStatus());
        assertEquals(GapReport.Status.ASSUMABLE, result.gap.getStatus());
        assertTrue(result.planWritten);
        assertTrue(new File(out, "plan.md").isFile());
        assertFalse(new File(out, "patches").exists());

        String facts = new String(Files.readAllBytes(new File(out, "facts.md").toPath()),
                Charset.forName("UTF-8"));
        assertFalse(facts.contains("PromotionRepository"), "must not invent Facts");

        String plan = new String(Files.readAllBytes(new File(out, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(plan.contains("绿场") || plan.contains("Clarification") || plan.contains("greenfield"));
        assertTrue(plan.contains("Task Breakdown"));
    }

    @Test
    void fuzzyAnswers_stayBlocked_noPlan() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace-promotion");
        File out = new File(module, "target/clarification-fuzzy-bundle-test");

        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                out.toPath(),
                RequirementAnalysisPipeline.promotionFuzzyAnswers(),
                true,
                "clarification-fuzzy");

        assertEquals(GapReport.Status.BLOCKED, result.gap.getStatus());
        assertFalse(result.gap.mayPlan());
        assertFalse(result.planWritten);
        assertFalse(new File(out, "plan.md").exists());

        String gap = new String(Files.readAllBytes(new File(out, "gap-report.md").toPath()),
                Charset.forName("UTF-8"));
        assertTrue(gap.contains("FUZZY") || gap.contains("Fuzzy"));
    }

    @Test
    void fuzzyDetector_rejectsHedgePhrases() {
        assertTrue(GapDetector.isFuzzyAnswer("应该有吧"));
        assertTrue(GapDetector.isFuzzyAnswer("Promotion 应该已经有了"));
        assertTrue(GapDetector.isFuzzyAnswer("大概支持"));
        assertTrue(GapDetector.isFuzzyAnswer("好像有 Order 接口"));
        assertFalse(GapDetector.isFuzzyAnswer(
                "否 — 绿场新建；本 fixture 无既有 Promotion 表"));
        assertFalse(GapDetector.isFuzzyAnswer(
                "按优惠金额比较：取减免金额最大者"));
    }
}
