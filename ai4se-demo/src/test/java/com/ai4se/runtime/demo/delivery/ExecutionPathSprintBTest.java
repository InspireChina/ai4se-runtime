package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.demo.analysis.PlanAcceptance;
import com.ai4se.runtime.demo.analysis.PlanDeclaredTargets;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.demo.input.InputDeliveryScenario;
import com.ai4se.runtime.demo.input.ProductionInputLoader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionPathSprintBTest {

    @Test
    void acceptanceMatrix_coversAllIds_andDetectsMissing() throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        File workspace = new File(module, "pilot-workspace");
        File analysisOut = new File(module, "target/execution-path-sprint-b-test/analysis");
        File patchesDir = new File(module, "execution-path-sprint-a-input/patches");
        File mappingFile = new File(module, "execution-path-sprint-a-input/verification-mapping.txt");

        RequirementAnalysisPipeline.Result analysis = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.DEFAULT_REQUIREMENT,
                analysisOut.toPath(),
                null,
                false,
                "execution-path-sprint-b-test");

        String plan = new String(Files.readAllBytes(new File(analysisOut, "plan.md").toPath()),
                Charset.forName("UTF-8"));
        Map<String, String> acceptance = PlanAcceptance.parse(plan);
        assertTrue(acceptance.containsKey("A1"));
        assertTrue(acceptance.containsKey("A2"));
        assertTrue(acceptance.containsKey("A3"));

        List<String> declared = PlanDeclaredTargets.parse(plan);
        Map<String, String> patches = ProductionInputLoader.readPatches(patchesDir.toPath());
        PatchCoverage coverage = PatchCoverage.compute(declared, patches);
        assertEquals(0, coverage.undeclaredCount);
        assertEquals(coverage.declaredCount, coverage.coveredCount);

        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", plan + (plan.endsWith("\n") ? "" : "\n"));
        File workDir = WorkspaceBootstrap.prepareRunWorkspace(
                module, "pilot-workspace", "execution-path-sprint-b-test-work");
        InputDeliveryScenario scenario = new InputDeliveryScenario(
                "execution-path-sprint-b-test",
                "timeout-acceptance-matrix",
                "execution-path-sprint-b-test",
                analysis.requirement,
                ProductionInputLoader.DEFAULT_DISCOVERY,
                "mvn -f pom.xml -q test",
                planFiles,
                patches);
        List<DeliveryStageResult> stages = SerialDeliveryRunner.run(workDir, scenario);
        assertTrue(stageOk(stages, "VERIFICATION"));

        Map<String, String> mapping = VerificationMapping.load(mappingFile.toPath());
        VerificationMatrix matrix = VerificationMatrix.build(acceptance, mapping, workDir.toPath());
        assertTrue(matrix.complete, "all acceptance must be COVERED");
        assertEquals(0, matrix.missing);

        Files.write(workDir.toPath().resolve("verification-matrix.md"),
                matrix.toMarkdown().getBytes(Charset.forName("UTF-8")));
        assertTrue(new File(workDir, "verification-matrix.md").isFile());
        assertTrue(matrix.toMarkdown().contains("COVERED"));

        Map<String, String> withMissing = new LinkedHashMap<String, String>(acceptance);
        withMissing.put("A99", "no mapping on purpose");
        VerificationMatrix incomplete = VerificationMatrix.build(withMissing, mapping, workDir.toPath());
        assertFalse(incomplete.complete);
        assertTrue(incomplete.missing >= 1);
        assertTrue(incomplete.toMarkdown().contains("MISSING"));
    }

    @Test
    void mappingAbsent_marksMissingNotPass() throws Exception {
        Map<String, String> acceptance = new LinkedHashMap<String, String>();
        acceptance.put("A1", "must be verified");
        Map<String, String> mapping = new LinkedHashMap<String, String>();
        VerificationMatrix matrix = VerificationMatrix.build(acceptance, mapping, null);
        assertEquals(1, matrix.missing);
        assertFalse(matrix.complete);
        assertEquals(VerificationMatrix.Status.MISSING, matrix.rows.get(0).status);
    }

    private static boolean stageOk(List<DeliveryStageResult> stages, String name) {
        for (DeliveryStageResult s : stages) {
            if (name.equals(s.getStage())) {
                return s.getResult().isSuccess();
            }
        }
        return false;
    }
}
