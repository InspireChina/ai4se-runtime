package com.ai4se.runtime.demo.pathway;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.lifecycle.OnboardPolicy;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Field pathway against a real customer repo.
 *
 * <p>Default {@code --script V3}: one Dev Adapter call, Verify once — fail stops (no Adapter retry).
 * {@code --script V4}: Dev → Verify FAIL → Defect → re-Dev → Verify PASS (Control-owned loop).
 * Does <b>not</b> overwrite an already-honest {@code entries.yaml}.
 *
 * <pre>
 * Required: --workspace --story --seed --allowed (repeatable) --verify-command
 * Adapter:  --adapter cursor|functional
 * Script:   --script V3|V4 (default V3)
 * Optional: --plan-summary --discovery-file --clarification-q --clarification-a
 * Approval: --approval-mode low-risk (default) | require-human | always; --narrow-test-only
 * Demo:     --preset findlast | --preset v4-yuantofen
 * </pre>
 */
public final class FieldPathwayMain {

    private static final String DEFAULT_NARROW_VERIFY =
            "mvn -pl yudao-framework/yudao-common -am -Dtest=CollectionUtilsTest -DfailIfNoTests=false test";
    private static final String DEFAULT_ALLOWED_MAIN =
            "yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/collection/CollectionUtils.java";
    private static final String DEFAULT_ALLOWED_TEST =
            "yudao-framework/yudao-common/src/test/java/cn/iocoder/yudao/framework/common/util/collection/CollectionUtilsTest.java";

    private static final String MONEY_UTILS_MAIN =
            "yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/number/MoneyUtils.java";
    private static final String MONEY_UTILS_TEST =
            "yudao-framework/yudao-common/src/test/java/cn/iocoder/yudao/framework/common/util/number/MoneyUtilsTest.java";
    private static final String MONEY_UTILS_VERIFY =
            "mvn -pl yudao-framework/yudao-common -am -Dtest=MoneyUtilsTest -DfailIfNoTests=false test";

    private FieldPathwayMain() {
    }

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        Path workspace = a.workspace.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            fail("workspace not found: " + workspace);
        }
        if (!Files.isRegularFile(workspace.resolve("pom.xml"))) {
            fail("not a Maven root: " + workspace);
        }
        if (Files.isDirectory(workspace.resolve(".story").resolve(a.storyId))) {
            if (!a.resume) {
                fail("Story already exists (refuse reuse): .story/" + a.storyId
                        + " — pick a new --story id or pass --resume");
            }
            System.out.println("resume=true story=" + a.storyId);
        }

        System.out.println("FieldPathway · workspace=" + workspace);
        System.out.println("adapter=" + a.adapter + " story=" + a.storyId + " script=" + a.script);

        Path onboardScript = resolveOnboardScript();
        System.out.println("onboard_script=" + onboardScript);
        OnboardRepoScript.run(workspace, onboardScript);
        ensureEntriesPresent(workspace, a.verifyCommand);
        WorkspaceSlotVerifier.requireValid(workspace);
        OnboardPolicy.requireSlotsAlreadyPresent(workspace);

        Path seed = materializeSeed(workspace, a.storyId, a.seedPath);
        ModelCliAdapter dev = buildAdapter(a, workspace);
        ModelCliAdapter analysis = buildAnalysisAdapter(a);
        ModelCliAdapter planAd = buildPlanAdapter(a);
        ModelCliAdapter reviewAd = buildReviewAdapter(a);
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();

        String verify = a.verifyCommand;
        PathwayRunner.Config.Builder cfg = PathwayRunner.Config.builder(workspace, a.storyId)
                .script(a.script)
                .suite("B")
                .seedPath(seed)
                .verifyCommand(verify)
                .planSummary(a.planSummary)
                .planApprover(a.planApprover)
                .planHumanOwned(true)
                .approvalMode(a.approvalMode)
                .approvalRequireTestPathsOnly(a.approvalRequireTestPathsOnly)
                .approvalNote(a.approvalMode == PathwayRunner.ApprovalMode.LOW_RISK_AUTO
                        ? "低风险自动批准：Allowed⊆hint 且 verify∈entries"
                        : (planAd != null
                                ? "人工批准（Plan Adapter 产出 + Field Allowed hint）"
                                : "人工批准：Allowed/Plan 由 Field 参数给定"))
                .changeNote(a.changeNote)
                .commitMessage("ai4se(field): " + a.storyId)
                .devAdapter(dev)
                .roleModels(a.roleModels)
                .adapterTimeout(Duration.ofMinutes(15))
                .v4FailMode(a.v4FailMode)
                .v4Round1Source(a.v4Round1Incomplete ? "incomplete_hook" : null)
                .resumeAfterStop(a.resume);
        System.out.println("approval_mode=" + a.approvalMode
                + (a.approvalRequireTestPathsOnly ? " (test-paths-only)" : ""));
        if (!a.roleModels.isEmpty()) {
            System.out.println("role_models=" + a.roleModels.byRole()
                    + (a.roleModels.defaultModel() == null ? "" : " default=" + a.roleModels.defaultModel()));
        }
        if (analysis != null) {
            cfg.analysisAdapter(analysis);
            System.out.println("analysis_adapter=" + analysis.name()
                    + modelNote(a.roleModels, "analysis"));
        }
        if (planAd != null) {
            cfg.planAdapter(planAd);
            System.out.println("plan_adapter=" + planAd.name()
                    + modelNote(a.roleModels, "planning"));
        }
        if (reviewAd != null) {
            cfg.reviewAdapter(reviewAd);
            System.out.println("review_adapter=" + reviewAd.name()
                    + modelNote(a.roleModels, "review"));
        } else if (a.allowReviewFixture) {
            cfg.allowReviewFixture(true);
            System.out.println("review_source=fixture (explicit --review-fixture)");
        } else {
            fail("Need --review-adapter <cursor|claude|...> or --review-fixture"
                    + " (第九环不可静默通过)");
        }
        cfg.assumablePolicy(PathwayRunner.AssumablePolicy.REQUIRE_ACK);
        System.out.println("assumable_policy=REQUIRE_ACK");
        System.out.println("dev_adapter=" + (dev == null ? "none" : dev.name())
                + modelNote(a.roleModels, "development"));
        if (a.discoverySkipExplicit) {
            cfg.discoverySkip(a.discoverySkipRationale, a.discoverySkipApprover);
            System.out.println("discovery=skip (human) rationale=" + a.discoverySkipRationale);
        } else if (!Strings.isBlank(a.discoveryBody)) {
            cfg.discoveryReportBody(a.discoveryBody);
        } else if (analysis == null) {
            fail("Need --discovery-file, discovery-skip, or --analysis-adapter");
        }
        for (String allowed : a.allowedFiles) {
            cfg.allowedFile(allowed);
        }
        if (!Strings.isBlank(a.clarificationQ) && !Strings.isBlank(a.clarificationA)) {
            cfg.clarificationResolved(a.clarificationQ, a.clarificationA, a.clarificationResolver);
        } else if (!Strings.isBlank(a.clarificationQ) && Strings.isBlank(a.clarificationA)) {
            cfg.clarificationQuestionOnly(a.clarificationQ);
            System.out.println("clarification=pending (will Stop if unanswered)");
        }

        if (a.awaitHumanAccept) {
            cfg.lifecycleMode(PathwayRunner.LifecycleMode.SKIP);
        } else {
            cfg.humanAccepter(a.humanAccepter)
                    .humanAcceptanceKind(HumanAcceptanceRecords.Kind.FIXTURE)
                    .humanAcceptanceNote("field non-await run")
                    .lifecycleMode(PathwayRunner.LifecycleMode.NOOP);
        }

        try {
            PathwayRunner.PathwayResult result = PathwayRunner.run(cfg.build(), invoker);
            System.out.println("final_status=" + result.finalState.status());
            System.out.println("commit=" + result.commitShaOrNull);
            System.out.println("evidence=" + result.evidenceRoot);
            System.out.println("spine_mode=" + result.spine.spineMode);
            System.out.println("adapter_kind=" + result.spine.adapterKind);
            if (result.finalState.status() != WorkflowStatus.COMPLETED) {
                System.exit(1);
            }
            if (a.awaitHumanAccept) {
                System.out.println();
                System.out.println("HUMAN GATE: review evidence then confirm acceptance offline.");
                System.out.println("  " + result.evidenceRoot);
            }
        } catch (StageGateException e) {
            System.err.println("STOPPED (no auto-retry): " + e.getMessage());
            System.err.println("Inspect .story/" + a.storyId + "/ for verification/defects.");
            System.exit(2);
        }
    }

    private static ModelCliAdapter buildAdapter(Args a, Path workspace) {
        if (a.script == Script.V4 && a.presetV4YuanToFen
                && a.v4FailMode == PathwayRunner.V4FailMode.SEEDED) {
            return buildV4YuanToFenAdapter(a, workspace);
        }
        if (a.script == Script.V4 && a.v4FailMode == PathwayRunner.V4FailMode.NATURAL) {
            if (a.v4Round1Incomplete) {
                System.out.println("v4_fail_mode=natural round1=incomplete_hook round2=cursor "
                        + "(not seeded yuanToFen)");
                return buildNaturalV4IncompleteThenCursorAdapter(a, workspace);
            }
            System.out.println("v4_fail_mode=natural (both rounds use --adapter; no seeded FAIL)");
        }
        if ("cursor".equalsIgnoreCase(a.adapter)) {
            CursorCliAdapter cursor = new CursorCliAdapter();
            System.out.println("cursor_bin=" + cursor.resolvedBinary()
                    + " (cursor.app => subcommand agent)");
            if ("agent".equals(cursor.resolvedBinary())) {
                System.err.println(
                        "WARN: resolved binary is bare 'agent'. Set AI4SE_CURSOR_BIN if invoke fails.");
            } else if (CursorCliAdapter.isCursorAppCli(cursor.resolvedBinary())
                    && !Files.isRegularFile(Paths.get(cursor.resolvedBinary()))) {
                fail("Cursor CLI path not found: " + cursor.resolvedBinary());
            }
            return cursor;
        }
        if ("claude".equalsIgnoreCase(a.adapter)) {
            ClaudeCliAdapter claude = new ClaudeCliAdapter();
            System.out.println("claude_bin=" + claude.resolvedBinary());
            return claude;
        }
        if ("functional".equalsIgnoreCase(a.adapter)) {
            if (!a.presetFindLast) {
                fail("functional adapter currently only supports --preset findlast or --preset v4-yuantofen");
            }
            AtomicBoolean once = new AtomicBoolean();
            return new FunctionalModelCliAdapter("field-functional-findlast", request -> {
                if (!once.compareAndSet(false, true)) {
                    return AdapterResult.failure(
                            -1, "", "", "second Dev call refused (no retry)",
                            Collections.<String, String>emptyMap());
                }
                try {
                    applyFindLastPreset(workspace);
                    return AdapterResult.ok(
                            0, "findLast applied", "", Collections.singletonMap("adapter", "functional"));
                } catch (IOException e) {
                    return AdapterResult.failure(
                            -1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
                }
            });
        }
        fail("Unknown --adapter " + a.adapter + " (use cursor|claude|functional)");
        return null;
    }

    /**
     * V4 field adapter: round-1 seeds a deliberate FAIL; round-2 is Cursor (or functional fix).
     * Control owns the FAIL→Defect→re-Dev loop — Adapter does not retry.
     */
    private static ModelCliAdapter buildV4YuanToFenAdapter(Args a, Path workspace) {
        final CursorCliAdapter cursor;
        if ("cursor".equalsIgnoreCase(a.adapter)) {
            cursor = new CursorCliAdapter();
            System.out.println("v4_dev=round1_seeded_fail,round2_cursor");
            System.out.println("cursor_bin=" + cursor.resolvedBinary());
        } else if ("functional".equalsIgnoreCase(a.adapter)) {
            cursor = null;
            System.out.println("v4_dev=round1_seeded_fail,round2_functional_fix");
        } else {
            fail("V4 preset v4-yuantofen requires --adapter cursor|functional");
            return null;
        }
        return new ModelCliAdapter() {
            @Override
            public String name() {
                return cursor != null ? "field-v4-yuantofen-cursor" : "field-v4-yuantofen-functional";
            }

            @Override
            public AdapterResult execute(com.ai4se.execution.api.AdapterRequest request) {
                String round = request.env() == null ? "" : request.env().get("AI4SE_DEV_ROUND");
                try {
                    if ("1".equals(round)) {
                        applyYuanToFen(workspace, true);
                        return AdapterResult.ok(
                                0,
                                "round1 seeded broken yuanToFen (expect Verify FAIL)",
                                "",
                                Collections.singletonMap("v4_round", "1"));
                    }
                    if ("2".equals(round)) {
                        if (cursor != null) {
                            return cursor.execute(request);
                        }
                        applyYuanToFen(workspace, false);
                        return AdapterResult.ok(
                                0,
                                "round2 functional fix yuanToFen",
                                "",
                                Collections.singletonMap("v4_round", "2"));
                    }
                    return AdapterResult.failure(
                            -1, "", "", "unexpected AI4SE_DEV_ROUND=" + round,
                            Collections.<String, String>emptyMap());
                } catch (IOException e) {
                    return AdapterResult.failure(
                            -1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
                }
            }
        };
    }

    /**
     * Natural V4 helper: Round1 leaves an incomplete fix (still red); Round2 Cursor finishes.
     * Not the seeded ×10 yuanToFen path — discloses {@code v4_round1_source: incomplete_hook}.
     */
    private static ModelCliAdapter buildNaturalV4IncompleteThenCursorAdapter(Args a, Path workspace) {
        final CursorCliAdapter cursor = new CursorCliAdapter();
        System.out.println("cursor_bin=" + cursor.resolvedBinary());
        return new ModelCliAdapter() {
            @Override
            public String name() {
                return "field-v4-natural-incomplete-then-cursor";
            }

            @Override
            public AdapterResult execute(com.ai4se.execution.api.AdapterRequest request) {
                String round = request.env() == null ? "" : request.env().get("AI4SE_DEV_ROUND");
                try {
                    if ("1".equals(round)) {
                        applyPriceAdd(workspace, false);
                        return AdapterResult.ok(
                                0,
                                "round1 incomplete priceAdd (null OK, scale still wrong; expect FAIL)",
                                "",
                                Collections.singletonMap("v4_round", "1"));
                    }
                    if ("2".equals(round)) {
                        return cursor.execute(request);
                    }
                    return AdapterResult.failure(
                            -1, "", "", "unexpected AI4SE_DEV_ROUND=" + round,
                            Collections.<String, String>emptyMap());
                } catch (IOException e) {
                    return AdapterResult.failure(
                            -1, "", "", e.getMessage(), Collections.<String, String>emptyMap());
                }
            }
        };
    }

    /** {@code complete=false}: null-safe but missing scale; {@code true}: full fix. */
    static void applyPriceAdd(Path workspace, boolean complete) throws IOException {
        Path main = workspace.resolve(MONEY_UTILS_MAIN);
        String src = new String(Files.readAllBytes(main), StandardCharsets.UTF_8);
        String method;
        if (complete) {
            method = ""
                    + "    public static BigDecimal priceAdd(BigDecimal a, BigDecimal b) {\n"
                    + "        if (a == null || b == null) {\n"
                    + "            return null;\n"
                    + "        }\n"
                    + "        return a.add(b).setScale(PRICE_SCALE, RoundingMode.HALF_UP);\n"
                    + "    }\n";
        } else {
            method = ""
                    + "    public static BigDecimal priceAdd(BigDecimal a, BigDecimal b) {\n"
                    + "        if (a == null || b == null) {\n"
                    + "            return null;\n"
                    + "        }\n"
                    + "        // incomplete: scale still wrong — expect Verify FAIL\n"
                    + "        return a.add(b);\n"
                    + "    }\n";
        }
        if (src.contains("priceAdd(")) {
            src = src.replaceAll(
                    "(?s)    public static BigDecimal priceAdd\\(BigDecimal a, BigDecimal b\\) \\{.*?\\n    \\}\\n",
                    method);
        } else {
            int end = src.lastIndexOf('}');
            src = src.substring(0, end) + "\n" + method + "}\n";
        }
        Files.write(main, src.getBytes(StandardCharsets.UTF_8));
    }

    private static ModelCliAdapter buildAnalysisAdapter(Args a) {
        return buildNamedCliAdapter(a.analysisAdapter, "analysis");
    }

    private static ModelCliAdapter buildPlanAdapter(Args a) {
        return buildNamedCliAdapter(a.planAdapter, "plan");
    }

    private static ModelCliAdapter buildReviewAdapter(Args a) {
        return buildNamedCliAdapter(a.reviewAdapter, "review");
    }

    private static ModelCliAdapter buildNamedCliAdapter(String kind, String label) {
        if (Strings.isBlank(kind) || "none".equalsIgnoreCase(kind)) {
            return null;
        }
        if ("cursor".equalsIgnoreCase(kind)) {
            return new CursorCliAdapter();
        }
        if ("claude".equalsIgnoreCase(kind)) {
            return new ClaudeCliAdapter();
        }
        fail("Unknown --" + label + "-adapter " + kind + " (use cursor|claude|none)");
        return null;
    }

    private static String modelNote(RoleModelConfig models, String role) {
        if (models == null || models.isEmpty()) {
            return "";
        }
        String m = models.resolve(role);
        return Strings.isBlank(m) ? "" : (" model=" + m);
    }

    private static Path resolveOnboardScript() {
        String env = System.getenv("AI4SE_HOME");
        if (!Strings.isBlank(env)) {
            Path p = Paths.get(env, "scripts/onboard-repo.sh");
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        try {
            URI uri = FieldPathwayMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path loc = Paths.get(uri);
            return OnboardRepoScript.resolveScript(loc.getParent());
        } catch (Exception e) {
            return OnboardRepoScript.resolveScript();
        }
    }

    private static void applyFindLastPreset(Path workspace) throws IOException {
        Path main = workspace.resolve(DEFAULT_ALLOWED_MAIN);
        Path test = workspace.resolve(DEFAULT_ALLOWED_TEST);
        String mainSrc = new String(Files.readAllBytes(main), StandardCharsets.UTF_8);
        if (!mainSrc.contains("findLast(")) {
            String hook = "    public static <T> T findFirst(Collection<T> from, Predicate<T> predicate) {";
            int idx = mainSrc.indexOf(hook);
            if (idx < 0) {
                throw new IOException("findFirst hook not found in CollectionUtils.java");
            }
            String insert = ""
                    + "    public static <T> T findLast(Collection<T> from, Predicate<T> predicate) {\n"
                    + "        if (CollUtil.isEmpty(from)) {\n"
                    + "            return null;\n"
                    + "        }\n"
                    + "        T last = null;\n"
                    + "        for (T item : from) {\n"
                    + "            if (predicate.test(item)) {\n"
                    + "                last = item;\n"
                    + "            }\n"
                    + "        }\n"
                    + "        return last;\n"
                    + "    }\n\n";
            mainSrc = mainSrc.substring(0, idx) + insert + mainSrc.substring(idx);
            Files.write(main, mainSrc.getBytes(StandardCharsets.UTF_8));
        }
        String testSrc = new String(Files.readAllBytes(test), StandardCharsets.UTF_8);
        if (!testSrc.contains("testFindLast")) {
            String method = ""
                    + "\n    @Test\n"
                    + "    public void testFindLast() {\n"
                    + "        assertEquals(null, CollectionUtils.findLast(Collections.<Dog>emptyList(), d -> true));\n"
                    + "        java.util.List<Dog> dogs = Arrays.asList(\n"
                    + "                new Dog(1, \"a\", \"a\"),\n"
                    + "                new Dog(2, \"b\", \"b\"),\n"
                    + "                new Dog(3, \"a2\", \"a\"));\n"
                    + "        Dog lastA = CollectionUtils.findLast(dogs, d -> \"a\".equals(d.getCode()));\n"
                    + "        assertEquals(Integer.valueOf(3), lastA.getId());\n"
                    + "        assertEquals(null, CollectionUtils.findLast(dogs, d -> \"z\".equals(d.getCode())));\n"
                    + "    }\n"
                    + "}\n";
            if (!testSrc.contains("import java.util.Collections;")) {
                testSrc = testSrc.replace(
                        "import java.util.Arrays;",
                        "import java.util.Arrays;\nimport java.util.Collections;");
            }
            int end = testSrc.lastIndexOf('}');
            testSrc = testSrc.substring(0, end) + method;
            Files.write(test, testSrc.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** V4 seed: broken=true uses ×10 (FAIL); broken=false uses ×100 (PASS). */
    static void applyYuanToFen(Path workspace, boolean broken) throws IOException {
        Path main = workspace.resolve(MONEY_UTILS_MAIN);
        Path test = workspace.resolve(MONEY_UTILS_TEST);
        String mainSrc = new String(Files.readAllBytes(main), StandardCharsets.UTF_8);
        String impl = broken
                ? "        return Integer.valueOf(yuan.movePointRight(1).intValue());\n"
                : "        return Integer.valueOf(yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue());\n";
        String method = ""
                + "\n    /**\n"
                + "     * 元转分\n"
                + "     *\n"
                + "     * @param yuan 元\n"
                + "     * @return 分\n"
                + "     */\n"
                + "    public static Integer yuanToFen(BigDecimal yuan) {\n"
                + "        if (yuan == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + impl
                + "    }\n"
                + "}\n";
        if (mainSrc.contains("yuanToFen(")) {
            // Replace existing body between yuanToFen and next method/closing — rewrite whole method via regex-ish cut
            int start = mainSrc.indexOf("    public static Integer yuanToFen(BigDecimal yuan)");
            if (start < 0) {
                start = mainSrc.indexOf("yuanToFen(BigDecimal");
                start = mainSrc.lastIndexOf("\n", start) + 1;
            }
            int brace = mainSrc.indexOf('{', start);
            int depth = 0;
            int end = -1;
            for (int i = brace; i < mainSrc.length(); i++) {
                char c = mainSrc.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            if (start < 0 || end < 0) {
                throw new IOException("cannot relocate yuanToFen in MoneyUtils.java");
            }
            // drop trailing class close if we included it — method only
            String replacement = ""
                    + "    public static Integer yuanToFen(BigDecimal yuan) {\n"
                    + "        if (yuan == null) {\n"
                    + "            return null;\n"
                    + "        }\n"
                    + impl
                    + "    }";
            mainSrc = mainSrc.substring(0, start) + replacement + mainSrc.substring(end);
        } else {
            int end = mainSrc.lastIndexOf('}');
            mainSrc = mainSrc.substring(0, end) + method;
        }
        if (!mainSrc.contains("RoundingMode") && !broken) {
            // RoundingMode already imported in MoneyUtils
        }
        Files.write(main, mainSrc.getBytes(StandardCharsets.UTF_8));

        String testSrc = new String(Files.readAllBytes(test), StandardCharsets.UTF_8);
        if (!testSrc.contains("testYuanToFen")) {
            String testMethod = ""
                    + "\n    @Test\n"
                    + "    public void testYuanToFen() {\n"
                    + "        assertEquals(Integer.valueOf(100), MoneyUtils.yuanToFen(new BigDecimal(\"1.00\")));\n"
                    + "        assertEquals(Integer.valueOf(1), MoneyUtils.yuanToFen(new BigDecimal(\"0.01\")));\n"
                    + "        assertNull(MoneyUtils.yuanToFen(null));\n"
                    + "    }\n"
                    + "}\n";
            int end = testSrc.lastIndexOf('}');
            testSrc = testSrc.substring(0, end) + testMethod;
            Files.write(test, testSrc.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Create entries only when missing or still a dishonest stub. Never clobber a valid list.
     */
    static void ensureEntriesPresent(Path workspace, String verifyCommand) throws IOException {
        Path entries = workspace.resolve(".ai4se/repository/entries.yaml");
        Files.createDirectories(entries.getParent());
        if (Files.isRegularFile(entries)) {
            String existing = new String(Files.readAllBytes(entries), StandardCharsets.UTF_8);
            List<String> problems = WorkspaceSlotVerifier.problems(workspace);
            boolean entriesOk = true;
            for (String p : problems) {
                if (p.contains("entries.yaml") || p.contains("build/") || p.contains("test/")) {
                    entriesOk = false;
                    break;
                }
            }
            if (entriesOk && !existing.contains("(fill)")) {
                System.out.println("entries.yaml kept (already present and honest)");
                return;
            }
            System.out.println("entries.yaml present but dishonest/incomplete — rewriting narrow defaults");
        } else {
            System.out.println("entries.yaml missing — writing narrow defaults");
        }
        String body = ""
                + "# Field narrow entries — edit freely; FieldPathway will not overwrite if honest\n"
                + "build:\n"
                + "  - mvn -pl yudao-framework/yudao-common -am -DskipTests package\n"
                + "test:\n"
                + "  - " + verifyCommand + "\n";
        Files.write(entries, body.getBytes(StandardCharsets.UTF_8));
    }

    private static Path materializeSeed(Path workspace, String storyId, Path seedPath) throws IOException {
        if (seedPath == null || !Files.isRegularFile(seedPath)) {
            fail("--seed must point to an existing requirement.md file");
        }
        Path out = workspace.resolve(".ai4se/field-seeds/" + storyId + "-requirement.md");
        Files.createDirectories(out.getParent());
        Files.copy(seedPath, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return out;
    }

    private static Path materializeClasspathSeed(Path workspace, String storyId, String resource)
            throws IOException {
        Path out = workspace.resolve(".ai4se/field-seeds/" + storyId + "-requirement.md");
        Files.createDirectories(out.getParent());
        try (InputStream in = FieldPathwayMain.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("classpath resource missing: " + resource);
            }
            byte[] buf = new byte[4096];
            int n;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            while ((n = in.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            Files.write(out, bos.toByteArray());
        }
        return out;
    }

    private static void fail(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private static final class Args {
        final Path workspace;
        final String storyId;
        final Path seedPath;
        final String adapter;
        final Script script;
        final boolean awaitHumanAccept;
        final String planApprover;
        final String humanAccepter;
        final String clarificationResolver;
        final String clarificationQ;
        final String clarificationA;
        final String planSummary;
        final String changeNote;
        final String discoveryBody;
        final String verifyCommand;
        final List<String> allowedFiles;
        final boolean presetFindLast;
        final boolean presetV4YuanToFen;
        final boolean discoverySkipExplicit;
        final String discoverySkipRationale;
        final String discoverySkipApprover;
        final String analysisAdapter;
        final String planAdapter;
        final String reviewAdapter;
        final boolean allowReviewFixture;
        final RoleModelConfig roleModels;
        final PathwayRunner.ApprovalMode approvalMode;
        final boolean approvalRequireTestPathsOnly;
        final boolean resume;
        final PathwayRunner.V4FailMode v4FailMode;
        final boolean v4Round1Incomplete;

        Args(
                Path workspace,
                String storyId,
                Path seedPath,
                String adapter,
                Script script,
                boolean awaitHumanAccept,
                String planApprover,
                String humanAccepter,
                String clarificationResolver,
                String clarificationQ,
                String clarificationA,
                String planSummary,
                String changeNote,
                String discoveryBody,
                String verifyCommand,
                List<String> allowedFiles,
                boolean presetFindLast,
                boolean presetV4YuanToFen,
                boolean discoverySkipExplicit,
                String discoverySkipRationale,
                String discoverySkipApprover,
                String analysisAdapter,
                String planAdapter,
                String reviewAdapter,
                boolean allowReviewFixture,
                RoleModelConfig roleModels,
                PathwayRunner.ApprovalMode approvalMode,
                boolean approvalRequireTestPathsOnly,
                boolean resume,
                PathwayRunner.V4FailMode v4FailMode,
                boolean v4Round1Incomplete) {
            this.workspace = workspace;
            this.storyId = storyId;
            this.seedPath = seedPath;
            this.adapter = adapter;
            this.script = script;
            this.awaitHumanAccept = awaitHumanAccept;
            this.planApprover = planApprover;
            this.humanAccepter = humanAccepter;
            this.clarificationResolver = clarificationResolver;
            this.clarificationQ = clarificationQ;
            this.clarificationA = clarificationA;
            this.planSummary = planSummary;
            this.changeNote = changeNote;
            this.discoveryBody = discoveryBody;
            this.verifyCommand = verifyCommand;
            this.allowedFiles = allowedFiles;
            this.presetFindLast = presetFindLast;
            this.presetV4YuanToFen = presetV4YuanToFen;
            this.discoverySkipExplicit = discoverySkipExplicit;
            this.discoverySkipRationale = discoverySkipRationale;
            this.discoverySkipApprover = discoverySkipApprover;
            this.analysisAdapter = analysisAdapter;
            this.planAdapter = planAdapter;
            this.reviewAdapter = reviewAdapter;
            this.allowReviewFixture = allowReviewFixture;
            this.roleModels = roleModels == null ? RoleModelConfig.empty() : roleModels;
            this.approvalMode = approvalMode;
            this.approvalRequireTestPathsOnly = approvalRequireTestPathsOnly;
            this.resume = resume;
            this.v4FailMode = v4FailMode;
            this.v4Round1Incomplete = v4Round1Incomplete;
        }

        static Args parse(String[] args) throws IOException {
            Path workspace = null;
            String storyId = null;
            Path seedPath = null;
            Path discoveryFile = null;
            String adapter = "cursor";
            Script script = Script.V3;
            boolean awaitHuman = true;
            boolean presetFindLast = false;
            boolean presetV4YuanToFen = false;
            String planApprover = "field-human";
            String humanAccepter = "field-human";
            String clarificationResolver = "field-human";
            String clarificationQ = null;
            String clarificationA = null;
            String planSummary = null;
            String changeNote = "在 Allowed 范围内实现";
            String verifyCommand = null;
            List<String> allowed = new ArrayList<String>();
            String discoverySkipRationale = null;
            String discoverySkipApprover = null;
            String analysisAdapter = "none";
            String planAdapter = "none";
            String reviewAdapter = "none";
            boolean allowReviewFixture = false;
            RoleModelConfig.Builder models = RoleModelConfig.builder();
            PathwayRunner.ApprovalMode approvalMode = PathwayRunner.ApprovalMode.LOW_RISK_AUTO;
            boolean approvalRequireTestPathsOnly = false;
            boolean resume = false;
            PathwayRunner.V4FailMode v4FailMode = PathwayRunner.V4FailMode.SEEDED;
            boolean v4Round1Incomplete = false;

            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("--workspace".equals(a) && i + 1 < args.length) {
                    workspace = Paths.get(args[++i]);
                } else if ("--story".equals(a) && i + 1 < args.length) {
                    storyId = args[++i];
                } else if ("--seed".equals(a) && i + 1 < args.length) {
                    seedPath = Paths.get(args[++i]);
                } else if ("--adapter".equals(a) && i + 1 < args.length) {
                    adapter = args[++i];
                } else if ("--analysis-adapter".equals(a) && i + 1 < args.length) {
                    analysisAdapter = args[++i];
                } else if ("--plan-adapter".equals(a) && i + 1 < args.length) {
                    planAdapter = args[++i];
                } else if ("--review-adapter".equals(a) && i + 1 < args.length) {
                    reviewAdapter = args[++i];
                } else if ("--review-fixture".equals(a)) {
                    allowReviewFixture = true;
                } else if ("--model".equals(a) && i + 1 < args.length) {
                    models.defaultModel(args[++i]);
                } else if ("--model-analysis".equals(a) && i + 1 < args.length) {
                    models.role("analysis", args[++i]);
                } else if ("--model-planning".equals(a) && i + 1 < args.length) {
                    models.role("planning", args[++i]);
                } else if ("--model-development".equals(a) && i + 1 < args.length) {
                    models.role("development", args[++i]);
                } else if ("--model-dev".equals(a) && i + 1 < args.length) {
                    models.role("development", args[++i]);
                } else if ("--model-review".equals(a) && i + 1 < args.length) {
                    models.role("review", args[++i]);
                } else if ("--model-acceptance".equals(a) && i + 1 < args.length) {
                    models.role("acceptance", args[++i]);
                } else if ("--approval-mode".equals(a) && i + 1 < args.length) {
                    String m = args[++i];
                    if ("low-risk".equalsIgnoreCase(m) || "low_risk_auto".equalsIgnoreCase(m)) {
                        approvalMode = PathwayRunner.ApprovalMode.LOW_RISK_AUTO;
                    } else if ("require-human".equalsIgnoreCase(m) || "human".equalsIgnoreCase(m)) {
                        approvalMode = PathwayRunner.ApprovalMode.REQUIRE_HUMAN;
                    } else if ("always".equalsIgnoreCase(m) || "always_record".equalsIgnoreCase(m)) {
                        approvalMode = PathwayRunner.ApprovalMode.ALWAYS_RECORD;
                    } else {
                        fail("Unknown --approval-mode " + m + " (low-risk|require-human|always)");
                    }
                } else if ("--narrow-test-only".equals(a)) {
                    approvalRequireTestPathsOnly = true;
                } else if ("--require-human-approval".equals(a)) {
                    approvalMode = PathwayRunner.ApprovalMode.REQUIRE_HUMAN;
                } else if ("--resume".equals(a)) {
                    resume = true;
                } else if ("--v4-fail-mode".equals(a) && i + 1 < args.length) {
                    String m = args[++i];
                    if ("natural".equalsIgnoreCase(m)) {
                        v4FailMode = PathwayRunner.V4FailMode.NATURAL;
                    } else if ("seeded".equalsIgnoreCase(m)) {
                        v4FailMode = PathwayRunner.V4FailMode.SEEDED;
                    } else {
                        fail("Unknown --v4-fail-mode " + m + " (seeded|natural)");
                    }
                } else if ("--v4-round1".equals(a) && i + 1 < args.length) {
                    String m = args[++i];
                    if ("incomplete".equalsIgnoreCase(m)) {
                        v4Round1Incomplete = true;
                    } else if ("adapter".equalsIgnoreCase(m) || "cursor".equalsIgnoreCase(m)) {
                        v4Round1Incomplete = false;
                    } else {
                        fail("Unknown --v4-round1 " + m + " (incomplete|adapter)");
                    }
                } else if ("--script".equals(a) && i + 1 < args.length) {
                    String s = args[++i];
                    if ("V4".equalsIgnoreCase(s)) {
                        script = Script.V4;
                    } else if ("V3".equalsIgnoreCase(s)) {
                        script = Script.V3;
                    } else {
                        fail("Unknown --script " + s + " (use V3|V4)");
                    }
                } else if ("--allowed".equals(a) && i + 1 < args.length) {
                    allowed.add(args[++i]);
                } else if ("--verify-command".equals(a) && i + 1 < args.length) {
                    verifyCommand = args[++i];
                } else if ("--plan-summary".equals(a) && i + 1 < args.length) {
                    planSummary = args[++i];
                } else if ("--discovery-file".equals(a) && i + 1 < args.length) {
                    discoveryFile = Paths.get(args[++i]);
                } else if ("--discovery-skip-rationale".equals(a) && i + 1 < args.length) {
                    discoverySkipRationale = args[++i];
                } else if ("--discovery-skip-approver".equals(a) && i + 1 < args.length) {
                    discoverySkipApprover = args[++i];
                } else if ("--clarification-q".equals(a) && i + 1 < args.length) {
                    clarificationQ = args[++i];
                } else if ("--clarification-a".equals(a) && i + 1 < args.length) {
                    clarificationA = args[++i];
                } else if ("--change-note".equals(a) && i + 1 < args.length) {
                    changeNote = args[++i];
                } else if ("--preset".equals(a) && i + 1 < args.length) {
                    String p = args[++i];
                    if ("findlast".equalsIgnoreCase(p)) {
                        presetFindLast = true;
                    } else if ("v4-yuantofen".equalsIgnoreCase(p)) {
                        presetV4YuanToFen = true;
                        script = Script.V4;
                    } else {
                        fail("Unknown --preset " + p + " (findlast|v4-yuantofen)");
                    }
                } else if ("--no-await-human".equals(a)) {
                    awaitHuman = false;
                } else if ("--plan-approver".equals(a) && i + 1 < args.length) {
                    planApprover = args[++i];
                } else if ("--help".equals(a) || "-h".equals(a)) {
                    System.out.println("Usage: FieldPathwayMain --workspace <dir> --story <id> "
                            + "--seed <requirement.md> --allowed <path> [--allowed ...] "
                            + "--verify-command <cmd> [--adapter cursor|claude|functional] "
                            + "[--script V3|V4] [--discovery-file <md> | "
                            + "--discovery-skip-rationale <why> --discovery-skip-approver <who> | "
                            + "--analysis-adapter cursor|claude] [--plan-adapter cursor|claude] "
                            + "[--review-adapter cursor|claude] [--review-fixture] "
                            + "[--model <default>] [--model-analysis <id>] [--model-planning <id>] "
                            + "[--model-development <id>] [--model-review <id>] [--model-acceptance <id>] "
                            + "[--approval-mode low-risk|require-human|always] [--narrow-test-only] "
                            + "[--resume] [--v4-fail-mode seeded|natural] [--v4-round1 incomplete|adapter] "
                            + "[--preset findlast|v4-yuantofen]");
                    System.out.println("Review: require --review-adapter or explicit --review-fixture "
                            + "(第九环不可静默通过). ASSUMABLE Gap requires ack (REQUIRE_ACK).");
                    System.out.println("Models: also .ai4se/runtime/role-models.yaml or "
                            + "AI4SE_MODEL / AI4SE_MODEL_ANALYSIS / … (CLI flags win).");
                    System.exit(0);
                }
            }

            if (workspace == null) {
                fail("Required: --workspace <dir>");
            }
            if (presetFindLast) {
                if (Strings.isBlank(storyId)) {
                    storyId = "story-yudao-findlast-" + System.currentTimeMillis();
                }
                if (seedPath == null) {
                    seedPath = materializeClasspathSeed(
                            workspace.toAbsolutePath().normalize(),
                            storyId,
                            "/field/yudao-story-findlast.md");
                }
                if (allowed.isEmpty()) {
                    allowed.add(DEFAULT_ALLOWED_MAIN);
                    allowed.add(DEFAULT_ALLOWED_TEST);
                }
                if (Strings.isBlank(verifyCommand)) {
                    verifyCommand = DEFAULT_NARROW_VERIFY;
                }
                if (Strings.isBlank(planSummary)) {
                    planSummary = "实现 CollectionUtils.findLast 并补测例；Allowed 仅限两文件。";
                }
                if (Strings.isBlank(clarificationQ)) {
                    clarificationQ = "findLast 无匹配时返回 Optional.empty 还是 null？";
                    clarificationA = "返回 null，与现有 findFirst 保持一致。";
                }
            }
            if (presetV4YuanToFen) {
                if (Strings.isBlank(storyId)) {
                    storyId = "story-yudao-v4-yuantofen";
                }
                if (seedPath == null) {
                    seedPath = materializeClasspathSeed(
                            workspace.toAbsolutePath().normalize(),
                            storyId,
                            "/field/yudao-story-v4-yuantofen.md");
                }
                if (allowed.isEmpty()) {
                    allowed.add(MONEY_UTILS_MAIN);
                    allowed.add(MONEY_UTILS_TEST);
                }
                if (Strings.isBlank(verifyCommand)) {
                    verifyCommand = MONEY_UTILS_VERIFY;
                }
                if (Strings.isBlank(planSummary)) {
                    planSummary = "新增 MoneyUtils.yuanToFen 并补测例；V4 要求首轮 Verify FAIL，再按 Defect 修复后 PASS。";
                }
                if (Strings.isBlank(clarificationQ)) {
                    clarificationQ = "元转分舍入与 null 行为？";
                    clarificationA = "null→null；非 null 按分整数，HALF_UP 到分；1元=100分。";
                }
                if (Strings.isBlank(changeNote) || "在 Allowed 范围内实现".equals(changeNote)) {
                    changeNote = "yuanToFen · V4 缺陷回环";
                }
            }

            if (Strings.isBlank(storyId)) {
                fail("Required: --story <id> (or --preset)");
            }
            if (seedPath == null || !Files.isRegularFile(seedPath.toAbsolutePath().normalize())) {
                fail("Required: --seed <requirement.md> (or --preset)");
            }
            if (allowed.isEmpty()) {
                fail("Required: at least one --allowed <repo-relative-path>");
            }
            if (Strings.isBlank(verifyCommand)) {
                fail("Required: --verify-command <cmd>");
            }
            if (Strings.isBlank(planSummary)) {
                planSummary = "在 Allowed 内实现；用客户仓验证命令验收。";
            }
            if (v4Round1Incomplete && v4FailMode != PathwayRunner.V4FailMode.NATURAL) {
                fail("--v4-round1 incomplete requires --v4-fail-mode natural");
            }
            if (script == Script.V4 && !presetV4YuanToFen) {
                if (v4FailMode != PathwayRunner.V4FailMode.NATURAL) {
                    fail("Custom V4 (no preset) requires --v4-fail-mode natural "
                            + "(seeded loop still uses --preset v4-yuantofen)");
                }
            }
            if (v4FailMode == PathwayRunner.V4FailMode.NATURAL && script != Script.V4) {
                fail("--v4-fail-mode natural requires --script V4 (or --preset v4-yuantofen)");
            }

            boolean useAnalysisAdapter = !Strings.isBlank(analysisAdapter)
                    && !"none".equalsIgnoreCase(analysisAdapter);
            boolean discoverySkipExplicit =
                    !Strings.isBlank(discoverySkipRationale) && !Strings.isBlank(discoverySkipApprover);
            if (!Strings.isBlank(discoverySkipRationale) ^ !Strings.isBlank(discoverySkipApprover)) {
                fail("discovery skip requires both --discovery-skip-rationale and --discovery-skip-approver");
            }
            if (discoverySkipExplicit && discoveryFile != null) {
                fail("Use either --discovery-file or discovery-skip flags, not both");
            }
            if (useAnalysisAdapter && (discoverySkipExplicit || discoveryFile != null)) {
                fail("Use --analysis-adapter alone for Discovery hang-in (no discovery-file/skip)");
            }

            String discoveryBody = null;
            if (useAnalysisAdapter) {
                discoveryBody = null;
            } else if (discoverySkipExplicit) {
                discoveryBody = null;
            } else if (discoveryFile != null) {
                Path df = discoveryFile.toAbsolutePath().normalize();
                if (!Files.isRegularFile(df)) {
                    fail("--discovery-file not found: " + df);
                }
                discoveryBody = new String(Files.readAllBytes(df), StandardCharsets.UTF_8);
            } else if (presetFindLast) {
                discoveryBody = ""
                        + "## 范围摸底（模块 yudao-common — 非整仓）\n\n"
                        + "### 涉及模块\n"
                        + "- `yudao-framework/yudao-common`\n\n"
                        + "### 相关路径\n"
                        + "- CollectionUtils.java / CollectionUtilsTest.java\n\n"
                        + "### 构建 / 测试入口\n"
                        + "- `" + verifyCommand + "`\n\n"
                        + "### 仅事实 — 不含改码建议\n";
            } else if (presetV4YuanToFen) {
                discoveryBody = ""
                        + "## 范围摸底（MoneyUtils · yuanToFen · V4）\n\n"
                        + "### 相关路径\n"
                        + "- MoneyUtils.java / MoneyUtilsTest.java\n\n"
                        + "### API 事实\n"
                        + "- 已有 fenToYuan；Story 开工时尚无 yuanToFen\n"
                        + "- 1 元 = 100 分\n\n"
                        + "### 验证命令\n"
                        + "- `" + verifyCommand + "`\n\n"
                        + "### 仅事实 — 不含改码建议\n";
            } else {
                fail("Required: --discovery-file <md> 或 discovery-skip 或 --analysis-adapter cursor");
            }

            return new Args(
                    workspace,
                    storyId,
                    seedPath.toAbsolutePath().normalize(),
                    adapter,
                    script,
                    awaitHuman,
                    planApprover,
                    humanAccepter,
                    clarificationResolver,
                    clarificationQ,
                    clarificationA,
                    planSummary,
                    changeNote,
                    discoveryBody,
                    verifyCommand,
                    Collections.unmodifiableList(new ArrayList<String>(allowed)),
                    presetFindLast,
                    presetV4YuanToFen,
                    discoverySkipExplicit,
                    discoverySkipRationale,
                    discoverySkipApprover,
                    analysisAdapter,
                    planAdapter,
                    reviewAdapter,
                    allowReviewFixture,
                    models.build(),
                    approvalMode,
                    approvalRequireTestPathsOnly,
                    resume,
                    v4FailMode,
                    v4Round1Incomplete);
        }
    }
}
