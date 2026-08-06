package com.ai4se.context.onboard;

import com.ai4se.runtime.common.util.ShellExecutable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** Invokes {@code scripts/onboard-repo.sh} for tests / tooling. */
public final class OnboardRepoScript {

    private OnboardRepoScript() {
    }

    public static Path resolveScript() {
        return resolveScript(Paths.get("").toAbsolutePath().normalize());
    }

    /** Walk upward from {@code start} (inclusive) looking for {@code scripts/onboard-repo.sh}. */
    public static Path resolveScript(Path start) {
        Path cwd = start == null ? Paths.get("").toAbsolutePath().normalize() : start.toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd.resolve("scripts/onboard-repo.sh"),
                cwd.resolve("../scripts/onboard-repo.sh"),
                cwd.getParent() != null ? cwd.getParent().resolve("scripts/onboard-repo.sh") : null
        };
        for (Path c : candidates) {
            if (c != null && Files.isRegularFile(c)) {
                return c.toAbsolutePath().normalize();
            }
        }
        Path p = cwd;
        for (int i = 0; i < 8 && p != null; i++) {
            Path script = p.resolve("scripts/onboard-repo.sh");
            if (Files.isRegularFile(script)) {
                return script.toAbsolutePath().normalize();
            }
            p = p.getParent();
        }
        throw new IllegalStateException("Cannot locate scripts/onboard-repo.sh from " + cwd);
    }

    public static void run(Path workspace) throws IOException, InterruptedException {
        run(workspace, resolveScript());
    }

    public static void run(Path workspace, Path script) throws IOException, InterruptedException {
        if (script == null || !Files.isRegularFile(script)) {
            throw new IOException("onboard script missing: " + script);
        }
        ProcessBuilder pb = new ProcessBuilder(
                ShellExecutable.resolve(), script.toString(), workspace.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line).append('\n');
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("onboard-repo.sh timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("onboard-repo.sh failed exit=" + process.exitValue() + "\n" + out);
        }
    }
}
