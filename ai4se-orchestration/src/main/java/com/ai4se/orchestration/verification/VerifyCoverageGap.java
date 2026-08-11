package com.ai4se.orchestration.verification;

import com.ai4se.runtime.common.util.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Diff × verify-entry coverage disclosure (problem class: incomplete entry consumption).
 *
 * <p>Does <b>not</b> change {@code entries.yaml} schema. Infers surfaces from changed paths and
 * from shell command strings already registered as test entries. Gaps are <b>disclosure only</b>
 * — never a StageGate hard fail (brownfield repos often lack frontend entries).
 */
public final class VerifyCoverageGap {

    public static final String SURFACE_FRONTEND = "frontend";
    public static final String SURFACE_BACKEND = "backend";

    private VerifyCoverageGap() {
    }

    /**
     * Surfaces touched by Diff that no listed verify command appears to cover.
     * Empty → report {@code coverage_gap: none}.
     */
    public static Assessment assess(List<String> changedPaths, List<String> verifyCommands) {
        Set<String> touched = surfacesTouched(changedPaths);
        Set<String> covered = surfacesCoveredByCommands(verifyCommands);
        List<String> gaps = new ArrayList<String>();
        for (String surface : touched) {
            if (!covered.contains(surface)) {
                gaps.add(surface);
            }
        }
        return new Assessment(
                Collections.unmodifiableSet(touched),
                Collections.unmodifiableSet(covered),
                Collections.unmodifiableList(gaps));
    }

    static Set<String> surfacesTouched(List<String> paths) {
        Set<String> out = new LinkedHashSet<String>();
        if (paths == null) {
            return out;
        }
        for (String raw : paths) {
            if (Strings.isBlank(raw)) {
                continue;
            }
            String p = normalize(raw);
            if (looksFrontend(p)) {
                out.add(SURFACE_FRONTEND);
            }
            if (looksBackend(p)) {
                out.add(SURFACE_BACKEND);
            }
        }
        return out;
    }

    static Set<String> surfacesCoveredByCommands(List<String> commands) {
        Set<String> out = new LinkedHashSet<String>();
        if (commands == null) {
            return out;
        }
        for (String raw : commands) {
            if (Strings.isBlank(raw)) {
                continue;
            }
            String c = raw.toLowerCase(Locale.ROOT);
            if (commandCoversFrontend(c)) {
                out.add(SURFACE_FRONTEND);
            }
            if (commandCoversBackend(c)) {
                out.add(SURFACE_BACKEND);
            }
        }
        return out;
    }

    static boolean looksFrontend(String path) {
        String p = normalize(path);
        if (p.contains("node_modules/") || p.startsWith("node_modules")) {
            return false;
        }
        if (p.endsWith(".vue") || p.endsWith(".tsx") || p.endsWith(".jsx")) {
            return true;
        }
        if (p.endsWith(".css") || p.endsWith(".scss") || p.endsWith(".less") || p.endsWith(".sass")) {
            return true;
        }
        if (p.contains("frontend")
                || p.contains("front-end")
                || p.contains("wmp-be-frontend")
                || p.contains("/web/")
                || p.contains("/client/")
                || p.contains("/ui/")) {
            return true;
        }
        if ((p.endsWith(".ts") || p.endsWith(".js") || p.endsWith(".mjs") || p.endsWith(".cjs"))
                && !p.contains("src/main/java")
                && !p.contains("src/test/java")
                && (p.contains("/views/")
                || p.contains("/components/")
                || p.contains("/pages/")
                || p.contains("packages/")
                || p.contains(".test.")
                || p.contains(".spec.")
                || p.contains("vitest")
                || p.contains("playwright"))) {
            return true;
        }
        return false;
    }

    static boolean looksBackend(String path) {
        String p = normalize(path);
        if (p.endsWith(".java") || p.endsWith(".kt") || p.endsWith(".kts") || p.endsWith(".groovy")) {
            return true;
        }
        if (p.contains("src/main/java") || p.contains("src/test/java") || p.contains("src/main/kotlin")) {
            return true;
        }
        if (p.contains("backend") || p.contains("/server/") || p.contains("-server/")) {
            return true;
        }
        return false;
    }

    static boolean commandCoversFrontend(String commandLower) {
        String c = commandLower;
        if (c.contains("npm") || c.contains("npx") || c.contains("yarn") || c.contains("pnpm")) {
            return true;
        }
        if (c.contains("vitest")
                || c.contains("playwright")
                || c.contains("vue-tsc")
                || c.contains("jest")
                || c.contains("cypress")) {
            return true;
        }
        if (c.contains("frontend")
                || c.contains("front-end")
                || c.contains("wmp-be-frontend")
                || c.contains("/web/")
                || c.contains("/client/")) {
            return true;
        }
        return false;
    }

    static boolean commandCoversBackend(String commandLower) {
        String c = commandLower;
        return c.contains("mvn")
                || c.contains("gradle")
                || c.contains("gradlew")
                || c.contains("./gradlew");
    }

    private static String normalize(String path) {
        return path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /** Immutable assessment for report fields. */
    public static final class Assessment {
        private final Set<String> touched;
        private final Set<String> covered;
        private final List<String> gaps;

        Assessment(Set<String> touched, Set<String> covered, List<String> gaps) {
            this.touched = touched;
            this.covered = covered;
            this.gaps = gaps;
        }

        public Set<String> touched() {
            return touched;
        }

        public Set<String> covered() {
            return covered;
        }

        public List<String> gaps() {
            return gaps;
        }

        public boolean hasGap() {
            return !gaps.isEmpty();
        }

        /** Single-line value for {@code coverage_gap:} — {@code none} or comma-separated surfaces. */
        public String gapLabel() {
            if (gaps.isEmpty()) {
                return "none";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < gaps.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(gaps.get(i));
            }
            return sb.toString();
        }

        public String detailLine() {
            return "diff_touched=" + join(touched)
                    + "; entries_cover=" + join(covered)
                    + "; disclosure_only=true";
        }

        private static String join(Set<String> set) {
            if (set == null || set.isEmpty()) {
                return "(none)";
            }
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String s : set) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(s);
            }
            return sb.toString();
        }
    }
}
