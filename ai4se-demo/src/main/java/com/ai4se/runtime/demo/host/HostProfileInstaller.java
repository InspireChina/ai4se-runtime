package com.ai4se.runtime.demo.host;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * Installs the deliberately thin, host-neutral integration material into a customer workspace.
 *
 * <p>This is not an IDE plugin installer.  It creates a reviewable bridge contract that any
 * terminal-capable host (Claude, Cursor, Codex, OMP, or another approved tool) can consume.  A
 * vendor-native profile may later translate the same contract, but must not duplicate runtime
 * policy in its prompt.
 */
public final class HostProfileInstaller {

    public static final String HOST_DIR = ".ai4se/host";
    public static final String INSTALLATION = "installation.properties";
    public static final String HOST_GUIDE = "AI4SE-HOST.md";
    public static final String TERMINAL_HOST = "terminal-host";
    public static final String PROTOCOL_VERSION = "1";

    private HostProfileInstaller() {
    }

    public static InstallResult install(Path workspace, String host, Path runtimeJar) throws IOException {
        if (workspace == null || !Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("install requires an existing customer workspace");
        }
        if (!TERMINAL_HOST.equals(normalizeHost(host))) {
            throw new IllegalArgumentException(
                    "unsupported host profile: " + host + " (currently terminal-host only)");
        }
        if (runtimeJar == null || !Files.isRegularFile(runtimeJar)) {
            throw new IllegalArgumentException("install requires an existing --runtime-jar file");
        }
        Path root = workspace.resolve(HOST_DIR);
        Files.createDirectories(root);
        Path installation = root.resolve(INSTALLATION);
        Path guide = root.resolve(HOST_GUIDE);
        String installationText = installationText(runtimeJar);
        String guideText = guideText(runtimeJar);

        requireAbsentOrEqual(installation, installationText);
        requireAbsentOrEqual(guide, guideText);
        writeIfAbsent(installation, installationText);
        writeIfAbsent(guide, guideText);
        return new InstallResult(root, Files.isRegularFile(installation), Files.isRegularFile(guide));
    }

    /** True only for the two files owned by this installer; never treats arbitrary .ai4se changes as safe. */
    public static boolean isManagedArtifactPath(Path workspace, String changedPath) {
        if (workspace == null || Strings.isBlank(changedPath)) {
            return false;
        }
        String path = changedPath.replace('\\', '/');
        String prefix = HOST_DIR + "/";
        if (!(path.equals(prefix + INSTALLATION) || path.equals(prefix + HOST_GUIDE))) {
            return false;
        }
        Path install = workspace.resolve(HOST_DIR).resolve(INSTALLATION);
        Path guide = workspace.resolve(HOST_DIR).resolve(HOST_GUIDE);
        if (!Files.isRegularFile(install) || !Files.isRegularFile(guide)) {
            return false;
        }
        try {
            String props = new String(Files.readAllBytes(install), StandardCharsets.UTF_8);
            return props.contains("host=" + TERMINAL_HOST + "\n")
                    && props.contains("bridge_protocol_version=" + PROTOCOL_VERSION + "\n")
                    && new String(Files.readAllBytes(guide), StandardCharsets.UTF_8)
                            .contains("# AI4SE Terminal Host Guide");
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeHost(String host) {
        if (Strings.isBlank(host)) {
            throw new IllegalArgumentException("host required");
        }
        return host.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String installationText(Path runtimeJar) {
        return "host=" + TERMINAL_HOST + "\n"
                + "bridge_protocol_version=" + PROTOCOL_VERSION + "\n"
                + "runtime_jar=" + runtimeJar.toAbsolutePath().normalize() + "\n"
                + "policy=host_model_candidate_only\n";
    }

    private static String guideText(Path runtimeJar) {
        String jar = runtimeJar.toAbsolutePath().normalize().toString();
        return "# AI4SE Terminal Host Guide\n\n"
                + "This workspace uses AI4SE as a delivery control plane. You are the current host model, "
                + "not the workflow owner. Never modify business source while preparing Discovery or Specification.\n\n"
                + "## Discovery\n\n"
                + "1. Run `java -jar " + jar + " bridge prepare-discovery --workspace . --candidate <id> --scope repository`.\n"
                + "2. Read the printed package path and its `manifest.md` / `model-input.md`.\n"
                + "3. Write only `.ai4se/knowledge-candidates/<id>/candidate.yaml` and `documents/*.md`.\n"
                + "4. Run `java -jar " + jar + " bridge submit-discovery --workspace . --candidate <id> --scope repository`.\n"
                + "5. Stop for a human knowledge approval; never promote knowledge yourself.\n\n"
                + "## Story specification\n\n"
                + "1. Capture raw customer input with the Runtime `intake` command.\n"
                + "2. Run `java -jar " + jar + " bridge prepare-specification --workspace . --story <id>`.\n"
                + "3. Read the package. Write only `.story/<id>/specification/` result files required by its manifest.\n"
                + "4. Run `java -jar " + jar + " bridge submit-specification --workspace . --story <id>`.\n"
                + "5. If clarification is required, display the exact questions and wait for a human answer. If a candidate "
                + "exists, wait for human freeze. Do not begin development.\n\n"
                + "## Safety\n\n"
                + "- Browser/relay material must already be captured as a local attachment or approved summary; do not bypass customer access policy.\n"
                + "- The Runtime controls freezes, scope, tests, retries and delivery. A passing model narrative is never delivery evidence.\n"
                + "- After human plan approval, the customer-approved unattended Adapter may run Development through local commit.\n";
    }

    private static void requireAbsentOrEqual(Path path, String expected) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        String actual = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (!expected.equals(actual)) {
            throw new IOException("Host profile conflict at " + path
                    + " — do not overwrite an existing customer integration");
        }
    }

    private static void writeIfAbsent(Path path, String value) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, value.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Small result suitable for a host command response. */
    public static final class InstallResult {
        private final Path root;
        private final boolean installationPresent;
        private final boolean guidePresent;

        InstallResult(Path root, boolean installationPresent, boolean guidePresent) {
            this.root = root;
            this.installationPresent = installationPresent;
            this.guidePresent = guidePresent;
        }

        public Path root() { return root; }
        public boolean installationPresent() { return installationPresent; }
        public boolean guidePresent() { return guidePresent; }
    }
}
