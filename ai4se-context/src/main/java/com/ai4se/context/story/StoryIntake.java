package com.ai4se.context.story;

import com.ai4se.context.packagebuild.ModelInputEnvelope;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures a user's unstructured Story input before any model is allowed to turn it into a
 * developable requirement. Raw input is immutable by convention: a later correction opens a new
 * Story or is recorded as an explicit clarification, never silently overwrites the request.
 */
public final class StoryIntake {

    public static final String INPUT_DIR = "input";
    public static final String RAW_REQUEST = "raw-request.md";
    public static final String INTAKE_PROPERTIES = "intake.properties";
    public static final String ATTACHMENTS_PROPERTIES = "attachments.properties";

    private StoryIntake() {
    }

    public static IntakeResult capture(
            Path workspace, String storyId, String rawRequest, List<Path> attachments) throws IOException {
        requireOnboarded(workspace);
        requireStoryId(storyId);
        if (Strings.isBlank(rawRequest)) {
            throw new IllegalArgumentException("raw request must not be blank");
        }
        Path story = workspace.resolve(".story").resolve(storyId);
        Path input = story.resolve(INPUT_DIR);
        Path raw = input.resolve(RAW_REQUEST);
        if (Files.exists(raw)) {
            throw new IOException("Story intake already captured: " + raw + " — do not overwrite raw input");
        }
        Files.createDirectories(input);
        Files.createDirectories(RequirementAttachmentSlot.dir(workspace, storyId));
        byte[] rawBytes = rawRequest.getBytes(StandardCharsets.UTF_8);
        Files.write(raw, rawBytes);

        List<Attachment> copied = copyAttachments(workspace, storyId, attachments);
        StringBuilder attachmentProps = new StringBuilder();
        attachmentProps.append("attachment_count=").append(copied.size()).append('\n');
        for (int i = 0; i < copied.size(); i++) {
            Attachment a = copied.get(i);
            int n = i + 1;
            attachmentProps.append("attachment.").append(n).append(".name=").append(a.name).append('\n');
            attachmentProps.append("attachment.").append(n).append(".sha256=").append(a.sha256).append('\n');
            attachmentProps.append("attachment.").append(n).append(".mime=").append(a.mime).append('\n');
            attachmentProps.append("attachment.").append(n).append(".purpose=unclassified_requirement_input\n");
        }
        Files.write(input.resolve(ATTACHMENTS_PROPERTIES),
                attachmentProps.toString().getBytes(StandardCharsets.UTF_8));
        String intake = ""
                + "status=RAW_CAPTURED\n"
                + "story_id=" + storyId + "\n"
                + "raw_request=.story/" + storyId + "/input/" + RAW_REQUEST + "\n"
                + "raw_request_sha256=" + ModelInputEnvelope.sha256(rawBytes) + "\n"
                + "attachments_manifest=.story/" + storyId + "/input/" + ATTACHMENTS_PROPERTIES + "\n"
                + "captured_at=" + Instant.now().toString() + "\n"
                + "next=SPECIFICATION_REQUIRED\n";
        Files.write(input.resolve(INTAKE_PROPERTIES), intake.getBytes(StandardCharsets.UTF_8));
        return new IntakeResult(story, raw, input.resolve(ATTACHMENTS_PROPERTIES), copied);
    }

    public static Path inputDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(INPUT_DIR);
    }

    private static void requireOnboarded(Path workspace) throws IOException {
        if (workspace == null || !Files.isDirectory(workspace.resolve(".ai4se"))
                || !Files.isDirectory(workspace.resolve(".story"))) {
            throw new IOException("Workspace not onboarded (missing .ai4se/ or .story/): " + workspace);
        }
    }

    private static void requireStoryId(String storyId) {
        if (Strings.isBlank(storyId) || !storyId.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException("Invalid story id: " + storyId);
        }
    }

    private static List<Attachment> copyAttachments(Path workspace, String storyId, List<Path> attachments)
            throws IOException {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Attachment> copied = new ArrayList<Attachment>();
        Path destination = RequirementAttachmentSlot.dir(workspace, storyId);
        for (Path input : attachments) {
            if (input == null || !Files.isRegularFile(input)) {
                throw new IOException("attachment not found or not a file: " + input);
            }
            String name = input.getFileName().toString();
            Path target = destination.resolve(name).normalize();
            if (!target.startsWith(destination) || Files.exists(target)) {
                throw new IOException("duplicate or unsafe attachment name: " + name);
            }
            byte[] bytes = Files.readAllBytes(input);
            Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
            String mime = Files.probeContentType(input);
            copied.add(new Attachment(name, ModelInputEnvelope.sha256(bytes),
                    Strings.isBlank(mime) ? "application/octet-stream" : mime));
        }
        return copied;
    }

    private static final class Attachment {
        final String name;
        final String sha256;
        final String mime;

        Attachment(String name, String sha256, String mime) {
            this.name = name;
            this.sha256 = sha256;
            this.mime = mime;
        }
    }

    public static final class IntakeResult {
        private final Path storyDir;
        private final Path rawRequest;
        private final Path attachmentsManifest;
        private final List<Attachment> attachments;

        IntakeResult(Path storyDir, Path rawRequest, Path attachmentsManifest, List<Attachment> attachments) {
            this.storyDir = storyDir;
            this.rawRequest = rawRequest;
            this.attachmentsManifest = attachmentsManifest;
            this.attachments = Collections.unmodifiableList(new ArrayList<Attachment>(attachments));
        }

        public Path storyDir() {
            return storyDir;
        }

        public Path rawRequest() {
            return rawRequest;
        }

        public Path attachmentsManifest() {
            return attachmentsManifest;
        }

        public int attachmentCount() {
            return attachments.size();
        }
    }
}
