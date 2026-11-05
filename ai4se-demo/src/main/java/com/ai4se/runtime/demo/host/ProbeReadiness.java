package com.ai4se.runtime.demo.host;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Checks observable probe prerequisites before approval, without running historical tests. */
final class ProbeReadiness {
    private static final Pattern NPM = Pattern.compile(
            "npm\\s+(?:--prefix\\s+([\\w./-]+)\\s+)?run\\s+([\\w:.-]+)");

    private ProbeReadiness() { }

    static void check(Path workspace, String story) throws Exception {
        Path root = workspace.resolve(".ai4se/acceptance-probes/" + story);
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        StringBuilder evidence = new StringBuilder("- 检查范围：shell 语法、声明的 npm 脚本是否存在。\n")
                .append("- 本检查未执行功能断言；依赖服务、运行数据和探针覆盖面仍见 Plan。\n");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> scripts = files.filter(p -> p.toString().endsWith(".sh")).collect(Collectors.toList());
            for (Path script : scripts) {
                ProcessInvoker.ProcessOutcome syntax = invoker.run(Arrays.asList("bash", "-n", script.toString()),
                        workspace, Collections.<String, String>emptyMap(), Duration.ofSeconds(15));
                if (syntax.exitCode != 0 || syntax.timedOut) {
                    throw new StageGateException("候选 Probe shell 语法不可执行: " + script.getFileName() + " " + syntax.stderr);
                }
                evidence.append("- shell syntax OK：").append(script.getFileName()).append('\n');
                String text = new String(Files.readAllBytes(script), StandardCharsets.UTF_8).replace("\\\n", " ");
                Matcher npm = NPM.matcher(text);
                while (npm.find()) {
                    Path module = npm.group(1) == null ? workspace : workspace.resolve(npm.group(1)).normalize();
                    if (!module.startsWith(workspace) || !Files.isRegularFile(module.resolve("package.json"))) {
                        throw new StageGateException("Probe npm 模块不存在: " + module);
                    }
                    String check = "const p=require(process.argv[1]);"
                            + "if(!p.scripts||!p.scripts[process.argv[2]]){console.error('missing npm script: '+process.argv[2]);process.exit(1)}";
                    ProcessInvoker.ProcessOutcome result = invoker.run(Arrays.asList("node", "-e", check,
                                    module.resolve("package.json").toString(), npm.group(2)), workspace,
                            Collections.<String, String>emptyMap(), Duration.ofSeconds(15));
                    if (result.exitCode != 0 || result.timedOut) {
                        throw new StageGateException("Probe 指向不可用的 npm 脚本: " + npm.group() + " " + result.stderr);
                    }
                    evidence.append("- npm script exists：").append(npm.group()).append('\n');
                }
            }
        }
        Path report = workspace.resolve(".story/" + story + "/controller/probe-readiness.md");
        Files.createDirectories(report.getParent());
        Files.write(report, evidence.toString().getBytes(StandardCharsets.UTF_8));
    }
}
