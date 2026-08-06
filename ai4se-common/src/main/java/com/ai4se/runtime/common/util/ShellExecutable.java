package com.ai4se.runtime.common.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared shell executable resolution for process launches.
 *
 * <p>Windows ships WSL-launcher stub {@code bash.exe} under System32/Sysnative that only
 * prompts to install WSL. PATH lookup may hit those stubs before a real bash (e.g. Git Bash).
 * All production {@code ProcessBuilder} shell launches must use {@link #resolve()} so this
 * class of failure is fixed once — not per call site.
 *
 * <p>Also: Windows {@code CreateProcess} cannot run shebang scripts ({@code #!/usr/bin/env bash})
 * directly (error=193). Use {@link #launchArgv(String, List)} so script CLIs go through bash.
 */
public final class ShellExecutable {

    private ShellExecutable() {
    }

    /** Resolve a usable bash. Non-Windows returns {@code "bash"}. */
    public static String resolve() {
        if (!isWindows()) {
            return "bash";
        }
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return "bash";
        }
        String windir = System.getenv("WINDIR");
        String stub1 = windir == null ? null : new File(windir, "System32\\bash.exe").getAbsolutePath();
        String stub2 = windir == null ? null : new File(windir, "Sysnative\\bash.exe").getAbsolutePath();
        for (String dir : path.split(File.pathSeparator)) {
            if (dir == null || dir.isEmpty()) {
                continue;
            }
            File candidate = new File(dir, "bash.exe");
            if (!candidate.isFile()) {
                continue;
            }
            String candidatePath = candidate.getAbsolutePath();
            if (isWslStub(candidatePath, stub1, stub2)) {
                continue;
            }
            return candidatePath;
        }
        return "bash";
    }

    /**
     * Build argv to launch {@code executable} with trailing args.
     * On Windows, shebang / {@code .sh} scripts are wrapped: {@code bash <script> <args...>}.
     * Bare PATH names and native {@code .exe/.cmd/.bat} are left as-is.
     */
    public static List<String> launchArgv(String executable, List<String> argsAfter) {
        if (Strings.isBlank(executable)) {
            throw new IllegalArgumentException("executable required");
        }
        List<String> out = new ArrayList<String>();
        if (needsShellWrapper(executable)) {
            out.add(resolve());
            out.add(executable);
        } else {
            out.add(executable);
        }
        if (argsAfter != null) {
            out.addAll(argsAfter);
        }
        return Collections.unmodifiableList(out);
    }

    /** True when Windows CreateProcess would reject this path as a non-PE script. */
    public static boolean needsShellWrapper(String executable) {
        if (!isWindows() || Strings.isBlank(executable)) {
            return false;
        }
        // Bare command (PATH lookup) — OS / PATHEXT handle .cmd/.exe
        if (!executable.contains("/") && !executable.contains("\\") && !executable.contains(":")) {
            return false;
        }
        Path path = Paths.get(executable);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")
                || name.endsWith(".cmd")
                || name.endsWith(".bat")
                || name.endsWith(".com")
                || name.endsWith(".ps1")) {
            return false;
        }
        if (name.endsWith(".sh")) {
            return true;
        }
        return hasShebang(path);
    }

    static boolean hasShebang(Path path) {
        try {
            byte[] all = Files.readAllBytes(path);
            if (all.length < 2) {
                return false;
            }
            int i = 0;
            // skip UTF-8 BOM
            if (all.length >= 3
                    && (all[0] & 0xFF) == 0xEF
                    && (all[1] & 0xFF) == 0xBB
                    && (all[2] & 0xFF) == 0xBF) {
                i = 3;
            }
            if (i + 1 >= all.length) {
                return false;
            }
            return all[i] == '#' && all[i + 1] == '!';
        } catch (IOException e) {
            return false;
        }
    }

    /** True when absolute path is a known Windows WSL placeholder launcher. */
    public static boolean isWslStub(String absolutePath) {
        if (absolutePath == null || !isWindows()) {
            return false;
        }
        String windir = System.getenv("WINDIR");
        String stub1 = windir == null ? null : new File(windir, "System32\\bash.exe").getAbsolutePath();
        String stub2 = windir == null ? null : new File(windir, "Sysnative\\bash.exe").getAbsolutePath();
        return isWslStub(absolutePath, stub1, stub2);
    }

    /**
     * Preflight: resolved shell must be usable. On Windows, refusing when resolve() still
     * points at a WSL stub (no real Git Bash on PATH).
     */
    public static void requireUsable() {
        String resolved = resolve();
        if (isWindows() && isWslStub(resolved)) {
            throw new IllegalStateException(
                    "ShellExecutable: only WSL stub bash on PATH — install Git Bash or fix PATH");
        }
        if (isWindows() && "bash".equals(resolved)) {
            // bare name may still resolve to stub via ProcessBuilder — require an absolute hit
            String path = System.getenv("PATH");
            boolean anyReal = false;
            if (path != null) {
                String windir = System.getenv("WINDIR");
                String stub1 = windir == null ? null : new File(windir, "System32\\bash.exe").getAbsolutePath();
                String stub2 = windir == null ? null : new File(windir, "Sysnative\\bash.exe").getAbsolutePath();
                for (String dir : path.split(File.pathSeparator)) {
                    if (dir == null || dir.isEmpty()) {
                        continue;
                    }
                    File candidate = new File(dir, "bash.exe");
                    if (candidate.isFile() && !isWslStub(candidate.getAbsolutePath(), stub1, stub2)) {
                        anyReal = true;
                        break;
                    }
                }
            }
            if (!anyReal) {
                throw new IllegalStateException(
                        "ShellExecutable: no non-stub bash.exe on PATH (Windows)");
            }
        }
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isWslStub(String candidatePath, String stub1, String stub2) {
        if (candidatePath == null) {
            return false;
        }
        if (stub1 != null && candidatePath.equalsIgnoreCase(stub1)) {
            return true;
        }
        if (stub2 != null && candidatePath.equalsIgnoreCase(stub2)) {
            return true;
        }
        String norm = candidatePath.replace('/', '\\').toLowerCase(Locale.ROOT);
        return norm.endsWith("\\system32\\bash.exe") || norm.endsWith("\\sysnative\\bash.exe");
    }
}
