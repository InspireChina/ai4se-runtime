package com.ai4se.runtime.demo.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class StopPipelineWiredTest {

    @Test
    void pipeline_writesStopDecision_onSecondBlockedRound() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace-promotion");
        File out = new File(module, "target/stop-pipeline-wired-test");

        RequirementAnalysisPipeline.Result r1 = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                new File(out, "r1").toPath(),
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "t-r1",
                1,
                false);
        assertFalse(r1.stop.isStop());
        assertFalse(Files.exists(new File(out, "r1/stop-decision.md").toPath()));

        RequirementAnalysisPipeline.Result r2 = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                new File(out, "r2").toPath(),
                RequirementAnalysisPipeline.promotionFuzzyAnswers(),
                true,
                "t-r2",
                2,
                true);
        assertTrue(r2.stop.isStop());
        assertFalse(r2.planWritten);
        assertTrue(Files.exists(new File(out, "r2/stop-decision.md").toPath()));
        assertFalse(Files.exists(new File(out, "r2/plan.md").toPath()));
    }
}
