package com.ai4se.runtime.demo.analysis;

import com.ai4se.runtime.demo.delivery.WorkspaceBootstrap;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pilot CLI — NL requirement → Delivery Bundle (analysis only).
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.PilotAnalysisMain
 * </pre>
 */
public final class PilotAnalysisMain {

    private PilotAnalysisMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        String requirement = RequirementAnalysisPipeline.DEFAULT_REQUIREMENT;
        File workspace = new File(module, "pilot-workspace");
        File out = new File(module, "target/pilot-delivery-bundle");
        Map<String, String> answers = new LinkedHashMap<String, String>();

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--requirement".equals(args[i]) && i + 1 < args.length) {
                    requirement = args[++i];
                } else if ("--workspace".equals(args[i]) && i + 1 < args.length) {
                    workspace = new File(args[++i]);
                } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                    out = new File(args[++i]);
                } else if ("--answer".equals(args[i]) && i + 1 < args.length) {
                    String pair = args[++i];
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        answers.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
            }
        }

        System.out.println("=== Pilot Requirement Analysis ===");
        System.out.println("requirement = " + requirement);
        System.out.println("workspace   = " + workspace.getAbsolutePath());
        System.out.println("out         = " + out.getAbsolutePath());

        RequirementAnalysisPipeline.Result result = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                requirement,
                out.toPath(),
                answers);

        System.out.println("gap_status  = " + result.gap.getStatus());
        System.out.println("plan        = " + result.planWritten);
        System.out.println("candidates  = " + result.context.getCandidateFiles());
        if (!result.planWritten) {
            System.err.println("Bundle incomplete: still BLOCKED — provide --answer or fix workspace.");
            System.exit(2);
        }
        System.out.println("Pilot Analysis OK — legal Delivery Bundle written (no patches/Execution).");
    }
}
