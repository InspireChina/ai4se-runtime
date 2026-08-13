package com.ai4se.execution.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterRequest;
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
        assertTrue(prompt.contains("Requirement、Allowed files、AC、验证命令齐全时写 CLEAR"), prompt);
        assertTrue(prompt.contains("真实的需求、环境、兼容性或数据假设"), prompt);
    }
}
