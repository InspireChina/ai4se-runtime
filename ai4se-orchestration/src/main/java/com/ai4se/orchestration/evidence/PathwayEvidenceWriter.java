package com.ai4se.orchestration.evidence;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Writes V3/V4 pathway-evidence under {@code .story/<id>/pathway-evidence/}.
 * Shape: templates/pathway-evidence/README.md + handbook §6.
 * meta must disclose fixture spine vs adapter-driven hang-in (no silent fake-green).
 */
public final class PathwayEvidenceWriter {

    private PathwayEvidenceWriter() {
    }

    public static Path evidenceRoot(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve("pathway-evidence");
    }

    public static Path write(
            Path workspace,
            String storyId,
            String suite,
            String script,
            String adapter,
            String waveNote) throws IOException {
        return write(workspace, storyId, suite, script, adapter, waveNote, SpineDisclosure.fixtureControl());
    }

    public static Path write(
            Path workspace,
            String storyId,
            String suite,
            String script,
            String adapter,
            String waveNote,
            SpineDisclosure spine) throws IOException {
        if (Strings.isBlank(suite) || (!"A".equalsIgnoreCase(suite) && !"B".equalsIgnoreCase(suite))) {
            throw new StageGateException("Evidence suite must be A or B");
        }
        if (Strings.isBlank(script) || (!"V3".equalsIgnoreCase(script) && !"V4".equalsIgnoreCase(script))) {
            throw new StageGateException("Evidence script must be V3 or V4");
        }
        if (spine == null) {
            throw new StageGateException("SpineDisclosure required");
        }
        Path storyRoot = workspace.resolve(".story").resolve(storyId);
        if (!Files.isDirectory(storyRoot)) {
            throw new StageGateException("Story missing for evidence: " + storyId);
        }
        Path root = evidenceRoot(workspace, storyId);
        Files.createDirectories(root);

        String adapterValue = Strings.isBlank(adapter) ? "none" : adapter.trim();
        if (spine.adapterInvoked && ("none".equalsIgnoreCase(adapterValue) || adapterValue.isEmpty())) {
            throw new StageGateException("adapterInvoked=true requires a real adapter id");
        }
        if (!spine.adapterInvoked && !"none".equalsIgnoreCase(adapterValue) && !"fixture".equalsIgnoreCase(adapterValue)) {
            // allow recording intended adapter while spine still fixture — but force honesty field
        }

        String meta = ""
                + "suite: " + suite.toUpperCase(Locale.ROOT) + "\n"
                + "script: " + script.toUpperCase(Locale.ROOT) + "\n"
                + "adapter: " + adapterValue + "\n"
                + "adapter_invoked: " + spine.adapterInvoked + "\n"
                + "adapter_roles: " + spine.adapterRoles + "\n"
                + "adapter_kind: " + spine.adapterKind + "\n"
                + "spine_mode: " + spine.spineMode + "\n"
                + "discovery_prepared_by_runner: " + spine.discoveryPreparedByRunner + "\n"
                + "approval_prepared_by_runner: " + spine.approvalPreparedByRunner + "\n"
                + "gap_prepared_by_runner: " + spine.gapPreparedByRunner + "\n"
                + "human_acceptance_kind: " + spine.humanAcceptanceKind + "\n"
                + "wave: " + (Strings.isBlank(waveNote) ? "W8" : waveNote.trim()) + "\n"
                + "story_id: " + storyId + "\n"
                + "signoff_claim: " + spine.signoffClaim() + "\n"
                + (Strings.isBlank(spine.extraMetaYaml) ? "" : spine.extraMetaYaml);
        Files.write(root.resolve("meta.yaml"), meta.getBytes(StandardCharsets.UTF_8));

        copyFileIfPresent(storyRoot.resolve("requirement.md"), root.resolve("story/requirement.md"));
        copyTreeIfPresent(storyRoot.resolve("packages"), root.resolve("packages"));
        copyTreeIfPresent(storyRoot.resolve("analysis"), root.resolve("gap-plan/analysis"));
        copyTreeIfPresent(storyRoot.resolve("planning"), root.resolve("gap-plan/planning"));
        copyTreeIfPresent(storyRoot.resolve("development"), root.resolve("gap-plan/development"));
        copyTreeIfPresent(storyRoot.resolve("execution"), root.resolve("execution"));
        copyTreeIfPresent(storyRoot.resolve("verification"), root.resolve("verification"));
        copyTreeIfPresent(storyRoot.resolve("defects"), root.resolve("defects"));
        copyTreeIfPresent(storyRoot.resolve("review"), root.resolve("review"));
        copyTreeIfPresent(storyRoot.resolve("delivery"), root.resolve("delivery"));
        copyTreeIfPresent(storyRoot.resolve("sessions"), root.resolve("sessions"));
        copyTreeIfPresent(storyRoot.resolve("compress"), root.resolve("compress"));
        copyTreeIfPresent(storyRoot.resolve("acceptance"), root.resolve("acceptance"));
        copyTreeIfPresent(storyRoot.resolve("lifecycle"), root.resolve("lifecycle"));

        writePackageHashes(root.resolve("packages"));

        boolean deliverySaysNoPush = deliveryDeclaresNoPush(storyRoot);
        String audit = ""
                + "# audit-host\n\n"
                + "- customer_body_in_platform_repo: false (fixture/pointer only)\n"
                + "- push: false\n"
                + "- delivery_declares_no_push: " + deliverySaysNoPush + "\n"
                + "- spine_mode: " + spine.spineMode + "\n"
                + "- adapter_invoked: " + spine.adapterInvoked + "\n"
                + "- adapter_kind: " + spine.adapterKind + "\n"
                + "- suite: " + suite.toUpperCase(Locale.ROOT) + "\n"
                + "- note: A绿≠B通; Stop收场≠通路通; hybrid/functional≠通路通; fixture_control≠Adapter挂机签收\n";
        Files.write(root.resolve("audit-host.md"), audit.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static boolean deliveryDeclaresNoPush(Path storyRoot) throws IOException {
        Path delivery = storyRoot.resolve("delivery").resolve("delivery.md");
        if (!Files.isRegularFile(delivery)) {
            return false;
        }
        String text = new String(Files.readAllBytes(delivery), StandardCharsets.UTF_8);
        return text.contains("pushed: false");
    }

    private static void writePackageHashes(Path packagesRoot) throws IOException {
        if (!Files.isDirectory(packagesRoot)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# package manifests hash\n\n");
        Files.walkFileTree(packagesRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("manifest.md")) {
                    String rel = packagesRoot.relativize(file).toString().replace('\\', '/');
                    sb.append("- ").append(rel).append(": ").append(sha256(file)).append('\n');
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Files.write(packagesRoot.resolve("HASHES.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder();
            for (byte b : dig) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IOException("hash failed: " + file, e);
        }
    }

    private static void copyFileIfPresent(Path src, Path dest) throws IOException {
        if (!Files.isRegularFile(src)) {
            return;
        }
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyTreeIfPresent(Path src, Path dest) throws IOException {
        if (!Files.isDirectory(src)) {
            return;
        }
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = dest.resolve(src.relativize(dir).toString());
                Files.createDirectories(rel);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = dest.resolve(src.relativize(file).toString());
                Files.createDirectories(rel.getParent());
                Files.copy(file, rel, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Honest disclosure of how the unmanned spine produced stage artifacts.
     * {@code fixture_control} = Runner prepared Discovery/Plan/Approval/DevMutation; Adapter not invoked.
     * {@code hybrid_adapter_dev} = Plan still fixture; Development submitted via Adapter (spine wiring).
     * {@code adapter_driven} = stages executed via Adapter submission (full hang-in).
     * {@code adapter_kind}: {@code none} | {@code functional_hook} (predetermined) | {@code model_cli}.
     */
    public static final class SpineDisclosure {
        public static final String MODE_FIXTURE = "fixture_control";
        public static final String MODE_HYBRID_ADAPTER_DEV = "hybrid_adapter_dev";
        public static final String MODE_ADAPTER = "adapter_driven";

        public static final String KIND_NONE = "none";
        public static final String KIND_FUNCTIONAL = "functional_hook";
        public static final String KIND_MODEL_CLI = "model_cli";

        public final String spineMode;
        public final boolean adapterInvoked;
        public final boolean discoveryPreparedByRunner;
        public final boolean approvalPreparedByRunner;
        public final boolean gapPreparedByRunner;
        public final String humanAcceptanceKind;
        public final String adapterRoles;
        public final String adapterKind;
        /** Optional extra YAML lines (must end with newline if non-blank). */
        public final String extraMetaYaml;

        public SpineDisclosure(
                String spineMode,
                boolean adapterInvoked,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner,
                String humanAcceptanceKind) {
            this(spineMode, adapterInvoked, discoveryPreparedByRunner, approvalPreparedByRunner,
                    true, humanAcceptanceKind, adapterInvoked ? "Development" : "none",
                    adapterInvoked ? KIND_MODEL_CLI : KIND_NONE, null);
        }

        public SpineDisclosure(
                String spineMode,
                boolean adapterInvoked,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner,
                String humanAcceptanceKind,
                String adapterRoles,
                String adapterKind) {
            this(spineMode, adapterInvoked, discoveryPreparedByRunner, approvalPreparedByRunner,
                    true, humanAcceptanceKind, adapterRoles, adapterKind, null);
        }

        public SpineDisclosure(
                String spineMode,
                boolean adapterInvoked,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner,
                boolean gapPreparedByRunner,
                String humanAcceptanceKind,
                String adapterRoles,
                String adapterKind,
                String extraMetaYaml) {
            this.spineMode = Strings.isBlank(spineMode) ? MODE_FIXTURE : spineMode.trim();
            this.adapterInvoked = adapterInvoked;
            this.discoveryPreparedByRunner = discoveryPreparedByRunner;
            this.approvalPreparedByRunner = approvalPreparedByRunner;
            this.gapPreparedByRunner = gapPreparedByRunner;
            this.humanAcceptanceKind =
                    Strings.isBlank(humanAcceptanceKind) ? "fixture" : humanAcceptanceKind.trim();
            this.adapterRoles = Strings.isBlank(adapterRoles) ? "none" : adapterRoles.trim();
            this.adapterKind = Strings.isBlank(adapterKind) ? KIND_NONE : adapterKind.trim();
            this.extraMetaYaml = extraMetaYaml;
        }

        public static SpineDisclosure fixtureControl() {
            return new SpineDisclosure(
                    MODE_FIXTURE, false, true, true, true, "fixture", "none", KIND_NONE, null);
        }

        public static SpineDisclosure fixtureControl(String humanAcceptanceKind) {
            return new SpineDisclosure(
                    MODE_FIXTURE, false, true, true, true, humanAcceptanceKind, "none", KIND_NONE, null);
        }

        public static SpineDisclosure hybridAdapterDev(String humanAcceptanceKind, String adapterKind) {
            return hybridAdapterDev(humanAcceptanceKind, adapterKind, true, true, true, "Development", null);
        }

        public static SpineDisclosure hybridAdapterDev(
                String humanAcceptanceKind,
                String adapterKind,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner) {
            return hybridAdapterDev(
                    humanAcceptanceKind,
                    adapterKind,
                    discoveryPreparedByRunner,
                    approvalPreparedByRunner,
                    true,
                    "Development",
                    null);
        }

        public static SpineDisclosure hybridAdapterDev(
                String humanAcceptanceKind,
                String adapterKind,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner,
                String adapterRoles) {
            return hybridAdapterDev(
                    humanAcceptanceKind,
                    adapterKind,
                    discoveryPreparedByRunner,
                    approvalPreparedByRunner,
                    true,
                    adapterRoles,
                    null);
        }

        public static SpineDisclosure hybridAdapterDev(
                String humanAcceptanceKind,
                String adapterKind,
                boolean discoveryPreparedByRunner,
                boolean approvalPreparedByRunner,
                boolean gapPreparedByRunner,
                String adapterRoles,
                String extraMetaYaml) {
            String kind = Strings.isBlank(adapterKind) ? KIND_MODEL_CLI : adapterKind.trim();
            String roles = Strings.isBlank(adapterRoles) ? "Development" : adapterRoles.trim();
            return new SpineDisclosure(
                    MODE_HYBRID_ADAPTER_DEV,
                    true,
                    discoveryPreparedByRunner,
                    approvalPreparedByRunner,
                    gapPreparedByRunner,
                    humanAcceptanceKind,
                    roles,
                    kind,
                    extraMetaYaml);
        }

        public String signoffClaim() {
            if (MODE_ADAPTER.equals(spineMode) && adapterInvoked) {
                return "adapter_hangin_candidate";
            }
            if (MODE_HYBRID_ADAPTER_DEV.equals(spineMode) && adapterInvoked) {
                if (KIND_FUNCTIONAL.equals(adapterKind)) {
                    return "adapter_spine_wiring_functional";
                }
                return "adapter_spine_wiring";
            }
            return "control_gates_and_orchestration_only";
        }
    }
}
