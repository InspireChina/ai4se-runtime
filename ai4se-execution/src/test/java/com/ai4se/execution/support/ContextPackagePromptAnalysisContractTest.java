package com.ai4se.execution.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextPackagePromptAnalysisContractTest {

    @TempDir
    Path temp;

    @Test
    void analysisPromptDistinguishesClearFromRealAssumableGaps() throws Exception {
        Path manifest = temp.resolve("manifest.md");
        Files.write(manifest, "# package\n".getBytes(StandardCharsets.UTF_8));
        AdapterRequest request = new AdapterRequest(
                temp, temp, "Analysis", "story-1", Duration.ofMinutes(1), Collections.<String, String>emptyMap());

        String prompt = ContextPackagePrompt.build(request, manifest);

        assertTrue(prompt.contains("assumable_gap_count=整数"), prompt);
        assertTrue(prompt.contains("实现策略、测试写法、局部重构选择不是 Gap"), prompt);
        assertTrue(prompt.contains("Requirement、Allowed files、AC 齐全且目标代码可读时写 CLEAR"), prompt);
        assertTrue(prompt.contains("仓库级 test=unknown、没有统一历史测试入口、或尚未存在本卡测试类"), prompt);
        assertTrue(prompt.contains("slices/verification-entry.yaml"), prompt);
        assertTrue(prompt.contains("真实的需求、环境、兼容性或数据假设"), prompt);
    }

    @Test
    void promptEmbedsModelWorkOrderWhenBuilderProvidedIt() throws Exception {
        Path manifest = temp.resolve("manifest.md");
        Files.write(manifest, "# package\n".getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(ModelInputEnvelope.FILE),
                "# AI4SE Model Work Order\n\n- AC: exact behavior\n"
                        .getBytes(StandardCharsets.UTF_8));
        AdapterRequest request = new AdapterRequest(
                temp, temp, "Development", "story-1", Duration.ofMinutes(1),
                Collections.<String, String>emptyMap());

        String prompt = ContextPackagePrompt.build(request, manifest);

        assertTrue(prompt.contains("AI4SE Model Work Order"), prompt);
        assertTrue(prompt.contains("AC: exact behavior"), prompt);
        assertTrue(prompt.contains("Manifest audit path"), prompt);
        assertTrue(prompt.contains("不执行 Maven/npm/Node/浏览器测试或其他验收命令"), prompt);
        assertTrue(prompt.contains("Verification Control 是唯一执行冻结 Probe 与质量门禁的角色"), prompt);
    }
}
