package com.ai4se.runtime.common.util;

import java.io.File;
import java.util.Locale;

/**
 * Shared shell executable resolution for process launches.
 *
 * <p>Windows ships WSL-launcher stub {@code bash.exe} under System32/Sysnative that only
 * prompts to install WSL. PATH lookup may hit those stubs before a real bash (e.g. Git Bash).
 * All production {@code ProcessBuilder} shell launches must use {@link #resolve()} so this
 * class of failure is fixed once — not per call site.
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
