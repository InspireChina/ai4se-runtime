package com.ai4se.runtime.demo.host;

import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.specification.SpecificationRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.verification.AcceptanceProbeCandidates;
import com.ai4se.orchestration.verification.AcceptanceProbeSet;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.execution.support.StoryAdapterLease;
import com.ai4se.runtime.demo.cli.Ai4seMain;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Durable customer-host entry. The existing production pathway remains the delivery authority. */
public final class CustomerDeliveryController {
    private CustomerDeliveryController() { }

    public static int run(String[] args) throws Exception {
        if (args.length == 0
                || "--help".equals(args[0])
                || "-h".equals(args[0])
                || "help".equalsIgnoreCase(args[0])) {
            System.out.println("deliver prepare --workspace <repo> --story <id> --request-file <md> --adapter codex|cursor|claude [--write-scope <源码目录或文件>]\n"
                    + "deliver answer --workspace <repo> --story <id> --answer <回答> --actor <姓名>\n"
                    + "deliver start --workspace <repo> --story <id> --approve <readiness_sha256> --actor <姓名>\n"
                    + "deliver status|report --workspace <repo> --story <id>\n"
                    + "prepare/start 启动后台任务；status 显示真实任务状态；report 输出次日验收报告。\n"
                    + "首次接入仍使用 Bundle 的 ai4se-flow bootstrap；Skill 负责在宿主隐藏这些内部参数。");
            return 0;
        }
        Options a = new Options(args);
        try {
            switch (a.action) {
                case "prepare":
                    requireClean(a);
                    if (Files.exists(a.root.resolve("config.properties"))) {
                        throw new StageGateException("此 Story 已存在；查看 status，或使用 answer 继续澄清，不能覆盖输入");
                    }
                    a.require("request-file");
                    String adapter = a.require("adapter");
                    if (!Arrays.asList("codex", "claude", "cursor").contains(adapter)) {
                        throw new StageGateException("只支持已注册的 codex/cursor/claude Adapter");
                    }
                    List<String> writeCeiling = a.writeScopes.isEmpty()
                            ? inferWriteCeiling(a.workspace) : validateWriteCeiling(a.workspace, a.writeScopes);
                    List<String> intake = a.command("intake", "--request-file", a.require("request-file"));
                    for (String attachment : a.attachments) {
                        intake.add("--attachment"); intake.add(attachment);
                    }
                    call(intake, 0);
                    Properties config = new Properties();
                    config.setProperty("adapter", adapter);
                    config.setProperty("model", a.values.getProperty("model", ""));
                    config.setProperty("max-dev-rounds", positive(a.values.getProperty("max-dev-rounds", "3")));
                    config.setProperty("timeout-minutes", positive(a.values.getProperty("timeout-minutes", "15")));
                    config.setProperty("write-scope.count", Integer.toString(writeCeiling.size()));
                    for (int i = 0; i < writeCeiling.size(); i++) {
                        config.setProperty("write-scope." + (i + 1), writeCeiling.get(i));
                    }
                    store(a.root.resolve("config.properties"), config);
                    return launch(a, "_prepare");
                case "answer":
                    requireIdle(a);
                    Properties job = load(a.root.resolve("job.properties"));
                    String status = job.getProperty("status", "");
                    if (!status.startsWith("NEEDS_")) {
                        throw new StageGateException("当前状态不是待澄清，不能在执行期注入新业务决定");
                    }
                    String command = "NEEDS_SPECIFICATION".equals(status) ? "answer-spec" : "answer";
                    if ("NEEDS_ASSUMPTIONS".equals(status)) {
                        if (!"yes".equals(a.values.getProperty("ack-assumptions"))) {
                            throw new StageGateException("请先展示 analysis/gap.report.md 的假设，用户明确确认后传 --ack-assumptions yes");
                        }
                        com.ai4se.orchestration.analysis.AssumableAckRecords.write(
                                a.workspace, a.story, a.require("actor"), a.require("answer"));
                    } else {
                        call(a.command(command, "--answer", a.require("answer"), "--actor", a.require("actor")), 0);
                    }
                    return launch(a, "_prepare");
                case "continue":
                    refreshInterruptedState(a);
                    requireIdle(a); requireClean(a);
                    Properties previous = load(a.root.resolve("job.properties"));
                    if (!"INTERRUPTED".equals(previous.getProperty("status"))
                            || !"_prepare".equals(previous.getProperty("phase"))) {
                        throw new StageGateException("continue 仅续接中断的开发前准备，不能重置已执行开发或终态卡");
                    }
                    return launch(a, "_prepare");
                case "start":
                    requireIdle(a);
                    if (!"READY".equals(load(a.root.resolve("job.properties")).getProperty("status"))) {
                        throw new StageGateException("只有 READY 的就绪包可批准启动；终态不能通过重复 start 重置预算");
                    }
                    verifyReadiness(a.workspace, a.story, a.require("approve"));
                    requireClean(a);
                    Properties approval = new Properties();
                    approval.setProperty("actor", oneLine(a.require("actor")));
                    approval.setProperty("readiness_sha256", a.require("approve"));
                    approval.setProperty("at", Instant.now().toString());
                    store(a.root.resolve("start-approval.properties"), approval);
                    return launch(a, "_execute");
                case "status":
                    printStatus(a);
                    return 0;
                case "report":
                    refreshInterruptedState(a);
                    Path report = writeReport(a.workspace, a.story);
                    System.out.println(read(report));
                    return 0;
                case "_prepare":
                case "_execute":
                    return worker(a);
                default: throw new IllegalArgumentException("未知 deliver 操作: " + a.action);
            }
        } catch (IllegalStateException | StageGateException e) {
            System.err.println("REFUSED: " + e.getMessage());
            return 50;
        }
    }

    private static int launch(Options a, String action) throws Exception {
        // Reservation and child handoff use one repository-wide OS lock. A second Story cannot
        // start while the first changes the same customer checkout.
        try (StoryAdapterLease ignored = repositoryLease(a.workspace)) {
            Properties active = load(activeFile(a.workspace));
            if ("LAUNCHING".equals(active.getProperty("status"))
                    && Instant.now().toEpochMilli() - Long.parseLong(active.getProperty("at_millis", "0")) < 60000) {
                throw new StageGateException("客户仓已有任务正在启动: " + active.getProperty("story"));
            }
            String token = UUID.randomUUID().toString();
            Path priorJob = a.root.resolve("job.properties");
            if (Files.isRegularFile(priorJob)) {
                Files.copy(priorJob, a.root.resolve("job-history-" + token + ".properties"));
            }
            Properties next = new Properties();
            next.setProperty("status", "LAUNCHING");
            next.setProperty("story", a.story);
            next.setProperty("token", token);
            next.setProperty("at_millis", Long.toString(Instant.now().toEpochMilli()));
            store(activeFile(a.workspace), next);
            writeJob(a, "LAUNCHING", action, "任务已提交；等待后台进程接管");
            List<String> argv = workerCommand(action, a, token);
            ProcessBuilder pb = new ProcessBuilder(argv).directory(a.workspace.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(a.root.resolve("worker.log").toFile()));
            pb.redirectErrorStream(true);
            Process child = pb.start();
            child.getOutputStream().close();
            if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                    && (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) || child.exitValue() != 0)) {
                throw new StageGateException("后台进程会话启动失败；请检查 worker.log");
            }
            System.out.println("controller=LAUNCHED\nstory=" + a.story + "\nevidence=" + a.root
                    + "\nnext=deliver status；准备就绪后只需确认一次开始无人值守");
        }
        return 0;
    }

    private static List<String> workerCommand(String action, Options a, String token) throws Exception {
        Path source = Paths.get(CustomerDeliveryController.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        List<String> cmd = new ArrayList<String>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        if (Files.isRegularFile(source)) {
            cmd.add("-jar"); cmd.add(source.toString());
        } else {
            cmd.add("-cp"); cmd.add(System.getProperty("java.class.path"));
            cmd.add(Ai4seMain.class.getName());
        }
        cmd.addAll(Arrays.asList("deliver", action, "--workspace", a.workspace.toString(),
                "--story", a.story, "--token", token));
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            // A new process alone still inherits the host terminal's process group. Bash job
            // control creates a separate group, nohup ignores terminal hangup, disown removes
            // shell ownership, and every argument remains an argv element (no interpolation).
            List<String> detached = new ArrayList<String>(Arrays.asList("/bin/bash", "-c",
                    "set -m; nohup \"$@\" </dev/null & disown", "ai4se-worker"));
            detached.addAll(cmd);
            return detached;
        }
        return cmd;
    }

    private static int worker(Options a) throws Exception {
        StoryAdapterLease lease = null;
        for (int attempt = 0; attempt < 100 && lease == null; attempt++) {
            try { lease = repositoryLease(a.workspace); }
            catch (IllegalStateException busy) { Thread.sleep(100); }
        }
        if (lease == null) { throw new StageGateException("后台任务无法取得客户仓任务锁"); }
        try (StoryAdapterLease held = lease) {
            Properties active = load(activeFile(a.workspace));
            if (!a.require("token").equals(active.getProperty("token"))
                    || !a.story.equals(active.getProperty("story"))) {
                throw new StageGateException("后台任务凭据已失效");
            }
            active.setProperty("status", "RUNNING"); store(activeFile(a.workspace), active);
            writeJob(a, "RUNNING", a.action, "后台任务运行中");
            try {
                if ("_prepare".equals(a.action)) { prepare(a); }
                else { execute(a); }
                return 0;
            } catch (Exception e) {
                writeJob(a, "STOPPED", a.action, e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.err);
                return 50;
            } finally {
                active.setProperty("status", "SETTLED"); store(activeFile(a.workspace), active);
                writeReport(a.workspace, a.story);
            }
        }
    }

    private static void prepare(Options a) throws Exception {
        if (ApprovalRecords.isApproved(a.workspace, a.story)) {
            throw new StageGateException("准备阶段已存在启动批准；拒绝在 prepare 中进入开发，请核实当前状态");
        }
        Properties config = load(a.root.resolve("config.properties"));
        Path requirement = a.workspace.resolve(".story/" + a.story + "/requirement.md");
        if (!Files.isRegularFile(requirement)) {
            call(modelCommand(a, config, "specify"), 0);
            if (SpecificationRecords.requireOutcome(a.workspace, a.story)
                    == SpecificationRecords.Outcome.CLARIFICATION_REQUIRED) {
                writeJob(a, "NEEDS_SPECIFICATION", "Specification", "请回答 specification/clarification.questions.md");
                return;
            }
            call(a.command("freeze-spec"), 0);
        }
        Path ledger = a.workspace.resolve(".story/" + a.story + "/run/state.properties");
        List<String> cmd;
        if (Files.isRegularFile(ledger)) {
            cmd = modelCommand(a, config, "resume");
        } else {
            cmd = modelCommand(a, config, "run");
            cmd.addAll(Arrays.asList("--requirement", requirement.toString(), "--max-dev-rounds",
                    config.getProperty("max-dev-rounds")));
            List<String> scopes = new ArrayList<String>();
            int count = Integer.parseInt(config.getProperty("write-scope.count", "0"));
            for (int i = 1; i <= count; i++) {
                scopes.add(config.getProperty("write-scope." + i));
            }
            if (scopes.isEmpty()) { throw new StageGateException("缺少已冻结的源码写入上限"); }
            for (String scope : scopes) { cmd.add("--write-scope"); cmd.add(scope); }
        }
        int exit = Ai4seMain.run(cmd.toArray(new String[0]));
        Properties run = load(ledger);
        if (!"STOPPED_NEEDS_PLAN_APPROVAL".equals(run.getProperty("terminal"))) {
            String terminal = run.getProperty("terminal", "UNKNOWN");
            boolean assumptions = com.ai4se.orchestration.analysis.GapRecords.hasReport(a.workspace, a.story)
                    && com.ai4se.orchestration.analysis.GapRecords.readStatus(a.workspace, a.story)
                    == com.ai4se.orchestration.analysis.GapStatus.ASSUMABLE;
            writeJob(a, assumptions ? "NEEDS_ASSUMPTIONS"
                            : "STOPPED_NEEDS_CLARIFICATION".equals(terminal) ? "NEEDS_ANALYSIS" : "STOPPED",
                    "Analysis/Planning", terminal + "; exit=" + exit + "; " + run.getProperty("detail", ""));
            return;
        }
        if (!AcceptanceProbeSet.hasManifest(a.workspace, a.story)) {
            AcceptanceProbeCandidates.freeze(a.workspace, a.story);
        }
        ProbeReadiness.check(a.workspace, a.story);
        String digest = freezeReadiness(a.workspace, a.story);
        writeJob(a, "READY", "DeliveryReadiness", "请审阅 readiness.md；批准指纹=" + digest);
    }

    private static void execute(Options a) throws Exception {
        Properties approval = load(a.root.resolve("start-approval.properties"));
        String actor = approval.getProperty("actor", "");
        if (actor.trim().isEmpty()) { throw new StageGateException("缺少启动批准人"); }
        verifyReadiness(a.workspace, a.story, approval.getProperty("readiness_sha256", ""));
        requireClean(a);
        ApprovalRecords.approvePlan(a.workspace, a.story, actor,
                "START_UNATTENDED readiness_sha256=" + approval.getProperty("readiness_sha256"));
        Properties config = load(a.root.resolve("config.properties"));
        int exit = Ai4seMain.run(modelCommand(a, config, "resume").toArray(new String[0]));
        Properties run = load(a.workspace.resolve(".story/" + a.story + "/run/state.properties"));
        String terminal = run.getProperty("terminal", "UNKNOWN");
        writeJob(a, "AWAITING_HUMAN_ACCEPTANCE".equals(terminal) && exit == 0
                ? "DELIVERED_AWAITING_ACCEPTANCE" : "STOPPED", terminal,
                "runtime_exit=" + exit + "; " + run.getProperty("detail", ""));
    }

    static List<String> inferWriteCeiling(Path workspace) throws IOException {
        List<String> scopes = new ArrayList<String>();
        // Inventory establishes only a ceiling. Planning proposes exact files, presented to the
        // customer before start. Never infer authorization to edit a whole repository or .ai4se.
        try (Stream<Path> paths = Files.walk(workspace, 3)) {
            paths.filter(Files::isDirectory).filter(p -> Arrays.asList("src", "app", "lib", "server", "client",
                            "pages", "components", "tests", "test", "include", "cmd", "internal", "pkg")
                            .contains(p.getFileName().toString()))
                    .filter(p -> !p.toString().contains("node_modules") && !p.toString().contains("/.git/"))
                    .map(workspace::relativize).map(p -> p.toString().replace('\\', '/'))
                    .sorted().forEach(scopes::add);
        }
        if (scopes.isEmpty()) {
            throw new StageGateException("未识别源码目录；由宿主根据目标模块提供源码位置后重建准备卡，不能默认为全仓可写");
        }
        return scopes;
    }

    static List<String> validateWriteCeiling(Path workspace, List<String> requested) throws IOException {
        List<String> result = new ArrayList<String>();
        Path realWorkspace = workspace.toRealPath();
        for (String raw : requested) {
            Path candidate = workspace.resolve(raw).normalize();
            if (!candidate.startsWith(workspace) || candidate.equals(workspace)
                    || !Files.exists(candidate)) {
                throw new StageGateException("源码范围必须是客户仓内已有的目录或文件: " + raw);
            }
            if (!candidate.toRealPath().startsWith(realWorkspace)) {
                throw new StageGateException("源码范围经符号链接越出客户仓: " + raw);
            }
            String rel = workspace.relativize(candidate).toString().replace('\\', '/');
            if (rel.equals(".git") || rel.startsWith(".git/") || rel.equals(".ai4se")
                    || rel.startsWith(".ai4se/") || rel.equals(".story") || rel.startsWith(".story/")) {
                throw new StageGateException("源码范围不可包含 Git/控制文件: " + raw);
            }
            if (!result.contains(rel)) { result.add(rel); }
        }
        if (result.isEmpty()) { throw new StageGateException("至少提供一条源码范围"); }
        return result;
    }

    static String freezeReadiness(Path workspace, String story) throws Exception {
        Path root = workspace.resolve(".story/" + story + "/controller");
        Files.createDirectories(root);
        Properties frozen = snapshot(workspace, story);
        String digest = digest(frozen);
        frozen.setProperty("readiness_sha256", digest);
        store(root.resolve("readiness.properties"), frozen);
        String body = "# 交付就绪确认\n\n- Story：" + story + "\n- 就绪指纹：`" + digest + "`\n"
                + "- 基线：`" + frozen.getProperty("head") + "`\n"
                + "- 运行配置：controller/config.properties（模型、超时、修复轮次）\n\n"
                + "请集中审阅冻结需求、全部业务回答、下述设计/风险、准确文件范围和 AC 验证方法。"
                + "确认后后台执行到本地交付或明确终态，第二天进行人工验收。\n\n"
                + "## 冻结需求与验收标准\n\n" + read(workspace.resolve(".story/" + story + "/requirement.md"))
                + "\n## 设计、改动、测试和影响\n\n"
                + read(workspace.resolve(".story/" + story + "/planning/plan.md"))
                + "\n## 验证能力检查\n\n" + read(root.resolve("probe-readiness.md"))
                + "\n## 确认方式\n\n在宿主回复“开始无人值守”。Skill 将本页指纹和你的身份传给控制器。"
                + "启动前任何冻结输入变化都会使本次确认失效。\n";
        Files.write(root.resolve("readiness.md"), body.getBytes(StandardCharsets.UTF_8));
        return digest;
    }

    static void verifyReadiness(Path workspace, String story, String approval) throws Exception {
        Properties frozen = load(workspace.resolve(".story/" + story + "/controller/readiness.properties"));
        if (approval.isEmpty() || !approval.equals(frozen.getProperty("readiness_sha256"))
                || !approval.equals(digest(snapshot(workspace, story)))) {
            throw new StageGateException("交付就绪输入已变化或批准指纹不匹配；请重新准备并审阅，不能沿用旧批准");
        }
    }

    private static Properties snapshot(Path workspace, String story) throws Exception {
        Properties result = new Properties();
        result.setProperty("head", WorkspaceGit.headSha(workspace, new ProcessInvoker.RealProcessInvoker()));
        Path runtime = Paths.get(CustomerDeliveryController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        result.setProperty("runtime_sha256", Files.isRegularFile(runtime)
                ? ModelInputEnvelope.sha256(Files.readAllBytes(runtime)) : "development-classpath");
        List<Path> roots = Arrays.asList(workspace.resolve(".story/" + story + "/requirement.md"),
                workspace.resolve(".story/" + story + "/input"),
                workspace.resolve(".story/" + story + "/requirement-attachments"),
                workspace.resolve(".story/" + story + "/planning"),
                workspace.resolve(".story/" + story + "/controller/config.properties"),
                workspace.resolve(".ai4se/acceptance-probes/" + story),
                workspace.resolve(".ai4se/repository"), workspace.resolve(".ai4se/index"),
                workspace.resolve(".ai4se/knowledge"), workspace.resolve(".ai4se/rules"),
                workspace.resolve(".ai4se/runtime"));
        for (Path root : roots) {
            if (!Files.exists(root)) { continue; }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    if (file.getFileName().toString().equals("approval.md")) { continue; }
                    result.setProperty(workspace.relativize(file).toString().replace('\\', '/'),
                            ModelInputEnvelope.sha256(Files.readAllBytes(file)));
                }
            }
        }
        return result;
    }

    private static String digest(Properties p) {
        List<String> keys = new ArrayList<String>(p.stringPropertyNames()); Collections.sort(keys);
        StringBuilder body = new StringBuilder();
        for (String key : keys) { body.append(key).append('=').append(p.getProperty(key)).append('\n'); }
        return ModelInputEnvelope.sha256(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> modelCommand(Options a, Properties c, String command) {
        List<String> cmd = a.command(command, "--adapter", c.getProperty("adapter"),
                "--timeout-minutes", c.getProperty("timeout-minutes"));
        if (!c.getProperty("model", "").isEmpty()) {
            cmd.add("--model"); cmd.add(c.getProperty("model"));
        }
        return cmd;
    }

    private static void call(List<String> cmd, int expected) throws Exception {
        int exit = Ai4seMain.run(cmd.toArray(new String[0]));
        if (exit != expected) { throw new StageGateException(cmd.get(0) + " exit=" + exit); }
    }

    private static void requireClean(Options a) throws Exception {
        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                a.workspace, a.story, new ProcessInvoker.RealProcessInvoker());
        if (!dirty.isEmpty()) { throw new StageGateException("客户仓已有未提交改动: " + dirty); }
    }

    private static void requireIdle(Options a) {
        try (StoryAdapterLease ignored = repositoryLease(a.workspace)) { /* fail immediately if active */ }
    }

    private static StoryAdapterLease repositoryLease(Path workspace) {
        return StoryAdapterLease.acquireFile(workspace.resolve(".ai4se/host/controller.lock"), "customer checkout");
    }

    private static Path activeFile(Path workspace) {
        return workspace.resolve(".ai4se/host/controller-active.properties");
    }

    private static void printStatus(Options a) throws Exception {
        refreshInterruptedState(a);
        Properties state = load(a.root.resolve("job.properties"));
        boolean running;
        try (StoryAdapterLease ignored = repositoryLease(a.workspace)) { running = false; }
        catch (IllegalStateException busy) { running = a.story.equals(load(activeFile(a.workspace)).getProperty("story")); }
        String status = state.getProperty("status", "NOT_STARTED");
        if ("RUNNING".equals(status) && !running) { status = "INTERRUPTED"; }
        System.out.println("controller=" + status + "\nworker_active=" + running + "\nstory=" + a.story
                + "\ndetail=" + state.getProperty("detail", "") + "\nreadiness=" + a.root.resolve("readiness.md")
                + "\nreport=" + a.root.resolve("acceptance-report.md") + "\nlog=" + a.root.resolve("worker.log"));
    }

    private static void refreshInterruptedState(Options a) throws Exception {
        Properties job = load(a.root.resolve("job.properties"));
        String status = job.getProperty("status", "");
        if (!"RUNNING".equals(status) && !"LAUNCHING".equals(status)) { return; }
        try (StoryAdapterLease ignored = repositoryLease(a.workspace)) {
            if ("LAUNCHING".equals(status) && Instant.now().toEpochMilli()
                    - Instant.parse(job.getProperty("updated_at")).toEpochMilli() < 60000) { return; }
            writeJob(a, "INTERRUPTED", job.getProperty("phase", ""),
                    "后台进程已不持有任务锁；保留现有代码与证据。检查 Runtime 账本及 Adapter 锁后决定恢复，不能直接重复开发");
        } catch (IllegalStateException active) {
            // The background worker is alive and owns the repository lock.
        }
    }

    static Path writeReport(Path workspace, String story) throws Exception {
        Path storyRoot = workspace.resolve(".story/" + story);
        Path root = storyRoot.resolve("controller"); Files.createDirectories(root);
        Properties job = load(root.resolve("job.properties"));
        Properties run = load(storyRoot.resolve("run/state.properties"));
        StringBuilder body = new StringBuilder("# 客户功能交付与次日验收报告\n\n");
        body.append("- Story：").append(story).append("\n- 控制器：").append(job.getProperty("status", "NOT_STARTED"))
                .append("\n- Runtime 终态：").append(run.getProperty("terminal", "尚未结算"))
                .append("\n- 开发/修复轮次：").append(run.getProperty("rounds_used", "0"))
                .append(" / ").append(run.getProperty("max_dev_rounds", "未启动"))
                .append("\n- Adapter：").append(run.getProperty("adapter", "未启动"))
                .append("\n- 模型：").append(run.getProperty("model", "未启动"))
                .append("\n- 详情：").append(job.getProperty("detail", ""))
                .append("\n- 人工验收：尚待工程师本地确认；后台任务不代为验收。\n");
        body.append("\n## 功能与规格\n\n").append("冻结规格：[requirement.md](../requirement.md)；")
                .append("交付前确认：[readiness.md](readiness.md)。\n");
        body.append("\n## 实际验收结果\n\n");
        Path verification = storyRoot.resolve("verification");
        Path latest = null; int round = -1;
        if (Files.isDirectory(verification)) {
            try (Stream<Path> reports = Files.list(verification)) {
                for (Path p : reports.collect(Collectors.toList())) {
                    String name = p.getFileName().toString();
                    if (name.matches("report-round-\\d+\\.md")) {
                        int n = Integer.parseInt(name.replace("report-round-", "").replace(".md", ""));
                        if (n > round) { round = n; latest = p; }
                    }
                }
            }
        }
        if (latest == null) { body.append("未产生 Verification 结果。不得声明功能完成。\n"); }
        else {
            body.append("证据：[第 ").append(round).append(" 轮报告](../verification/")
                    .append(latest.getFileName()).append(")\n\n");
            for (String line : Files.readAllLines(latest, StandardCharsets.UTF_8)) {
                if (line.contains("verdict:") || line.startsWith("- outcome:")
                        || line.startsWith("- acceptance_all_proven:") || line.startsWith("- command:")) {
                    body.append(line).append('\n');
                }
            }
            body.append("\nPROVEN 表示冻结探针证明了对应断言；不代表整个系统无 Bug。探针覆盖不到的环境/UI 操作仍须本地验收。\n");
        }
        body.append("\n## Review 与交付\n\n");
        Path review = storyRoot.resolve("review/review-result.properties");
        body.append(Files.isRegularFile(review) ? "```properties\n" + read(review) + "\n```\n" : "Review 尚未产生。\n");
        Path delivery = storyRoot.resolve("delivery/delivery-report.md");
        body.append(Files.isRegularFile(delivery) ? read(delivery) : "尚无交付报告；以 Runtime 终态为准。\n");
        body.append("\n## 次日本地验收\n\n1. 先检查本页终态。只有 DELIVERED_AWAITING_ACCEPTANCE 才表示后台已本地交付。\n")
                .append("2. 对照 requirement.md 的每条 AC、planning/api-contract.md（如有）和实际 commit 检查功能。\n")
                .append("3. 按 planning/plan.md 的 Test Strategy 与 verification/ 日志复跑相关验证；")
                .append("依照 .ai4se/knowledge/testing-and-operations.md（如有）启动项目，未知启动条件应明确补齐。\n")
                .append("4. 在宿主回复“验收通过”，或描述复现步骤、预期/实际结果创建修复卡。\n")
                .append("5. 本工具未 push、部署或操作生产数据；失败记录保留，不将失败计作交付。\n");
        Path report = root.resolve("acceptance-report.md");
        Files.write(report, body.toString().getBytes(StandardCharsets.UTF_8)); return report;
    }

    private static void writeJob(Options a, String status, String phase, String detail) throws IOException {
        Properties p = new Properties(); p.setProperty("status", status); p.setProperty("phase", phase);
        p.setProperty("detail", oneLine(detail)); p.setProperty("updated_at", Instant.now().toString());
        store(a.root.resolve("job.properties"), p);
    }

    static Properties load(Path path) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(path)) { try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); } }
        return p;
    }

    static void store(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), ".controller-", ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { properties.store(w, "AI4SE controller"); }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    private static String read(Path path) throws IOException {
        return Files.isRegularFile(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "(文件未产生)\n";
    }
    private static String oneLine(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\r', ' '); }
    private static String positive(String n) {
        if (Integer.parseInt(n) < 1) { throw new IllegalArgumentException("预算必须大于 0"); } return n;
    }

    private static final class Options {
        final String action; final Properties values = new Properties();
        final List<String> attachments = new ArrayList<String>();
        final List<String> writeScopes = new ArrayList<String>();
        final Path workspace; final String story; final Path root;
        Options(String[] args) {
            action = args[0];
            for (int i = 1; i < args.length; i += 2) {
                if (!args[i].startsWith("--") || i + 1 == args.length) { throw new IllegalArgumentException("参数需要值: " + args[i]); }
                String key = args[i].substring(2);
                if ("attachment".equals(key)) { attachments.add(args[i + 1]); }
                else if ("write-scope".equals(key)) { writeScopes.add(args[i + 1]); }
                else { values.setProperty(key, args[i + 1]); }
            }
            workspace = Paths.get(require("workspace")).toAbsolutePath().normalize();
            story = require("story");
            if (!story.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) { throw new IllegalArgumentException("无效 Story id"); }
            root = workspace.resolve(".story/" + story + "/controller");
        }
        String require(String key) {
            String value = values.getProperty(key, "").trim();
            if (value.isEmpty()) { throw new IllegalArgumentException("缺少 --" + key); } return value;
        }
        List<String> command(String name, String... more) {
            List<String> out = new ArrayList<String>(Arrays.asList(name, "--workspace", workspace.toString(), "--story", story));
            out.addAll(Arrays.asList(more)); return out;
        }
    }
}
