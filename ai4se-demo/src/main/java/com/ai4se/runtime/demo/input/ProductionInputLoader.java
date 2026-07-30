package com.ai4se.runtime.demo.input;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production Input Loader — files → {@link DeliveryScenario}.
 * Lives outside Runtime Kernel (Demo / CLI layer).
 */
public final class ProductionInputLoader {

    public static final String DEFAULT_DISCOVERY = "echo skip-discovery";

    private ProductionInputLoader() {
    }

    public static DeliveryScenario load(Path inputDir) throws IOException {
        if (inputDir == null || !Files.isDirectory(inputDir)) {
            throw new IOException("input directory not found: " + inputDir);
        }
        Path requirement = inputDir.resolve("requirement.md");
        Path profile = inputDir.resolve("profile.yaml");
        Path verify = inputDir.resolve("verify.yaml");
        Path plan = inputDir.resolve("plan.md");
        Path patches = inputDir.resolve("patches");
        Path discovery = inputDir.resolve("discovery.yaml");

        requireFile(requirement, "requirement.md");
        requireFile(profile, "profile.yaml");
        requireFile(verify, "verify.yaml");
        requireFile(plan, "plan.md");
        if (!Files.isDirectory(patches)) {
            throw new IOException("missing required directory: patches/ under " + inputDir);
        }

        Map<String, String> profileMap = FlatYaml.read(profile);
        String id = FlatYaml.require(profileMap, "id", profile);
        String typeLabel = profileMap.containsKey("typeLabel")
                ? profileMap.get("typeLabel")
                : id;
        String projectId = profileMap.containsKey("projectId")
                ? profileMap.get("projectId")
                : "production-input";

        Map<String, String> verifyMap = FlatYaml.read(verify);
        String verifyCommand = FlatYaml.require(verifyMap, "command", verify);

        String discoveryCommand = DEFAULT_DISCOVERY;
        if (Files.isRegularFile(discovery)) {
            Map<String, String> discoveryMap = FlatYaml.read(discovery);
            String cmd = discoveryMap.get("command");
            if (cmd != null && !cmd.trim().isEmpty()) {
                discoveryCommand = cmd.trim();
            }
        }

        String requirementText = readUtf8(requirement).trim();
        if (requirementText.isEmpty()) {
            throw new IOException("requirement.md is empty");
        }
        String planText = readUtf8(plan);
        Map<String, String> planFiles = new LinkedHashMap<String, String>();
        planFiles.put("PLAN.md", planText.endsWith("\n") ? planText : planText + "\n");

        Map<String, String> executionFiles = readPatchTree(patches);
        if (executionFiles.isEmpty()) {
            throw new IOException("patches/ contains no files");
        }

        return new InputDeliveryScenario(
                id,
                typeLabel,
                projectId,
                requirementText,
                discoveryCommand,
                verifyCommand,
                planFiles,
                executionFiles);
    }

    private static void requireFile(Path file, String name) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing required file: " + name);
        }
    }

    private static String readUtf8(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, Charset.forName("UTF-8"));
    }

    public static Map<String, String> readPatches(Path patchesRoot) throws IOException {
        if (patchesRoot == null || !Files.isDirectory(patchesRoot)) {
            throw new IOException("patches directory not found: " + patchesRoot);
        }
        return readPatchTree(patchesRoot);
    }

    private static Map<String, String> readPatchTree(final Path patchesRoot) throws IOException {
        final Map<String, String> files = new LinkedHashMap<String, String>();
        Files.walkFileTree(patchesRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = patchesRoot.relativize(file);
                String key = relative.toString().replace('\\', '/');
                files.put(key, readUtf8(file));
                return FileVisitResult.CONTINUE;
            }
        });
        return Collections.unmodifiableMap(files);
    }
}
