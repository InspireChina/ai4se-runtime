package com.ai4se.context.codegraph;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Enumerates scannable source files with stable ordering; skips build and VCS noise. */
public final class SourceWalker {

    private SourceWalker() {
    }

    public static List<Path> listSourceFiles(Path workspace) throws IOException {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return Collections.emptyList();
        }
        final Path root = workspace.toAbsolutePath().normalize();
        final List<Path> files = new ArrayList<Path>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                String rel = root.relativize(dir).toString().replace('\\', '/');
                if (shouldSkipDirectory(name, rel)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && isSupportedSource(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);
        return files;
    }

    public static boolean shouldSkipDirectory(String name, String relativePath) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String rel = relativePath == null ? "" : relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (n.equals(".git") || n.equals("target") || n.equals("node_modules") || n.equals("build")
                || n.equals("dist") || n.equals(".idea") || n.equals(".story") || n.equals(".ai4se")
                || n.equals(".gradle") || n.equals("out") || n.equals("coverage") || n.equals(".cursor")) {
            return true;
        }
        if (rel.startsWith(".git/") || rel.equals(".git")) {
            return true;
        }
        if (rel.contains("/target/") || rel.endsWith("/target") || rel.equals("target")) {
            return true;
        }
        if (rel.contains("/node_modules/") || rel.equals("node_modules")) {
            return true;
        }
        return false;
    }

    public static boolean isSupportedSource(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".js")
                || name.endsWith(".vue")
                || name.endsWith(".ts")
                || name.endsWith(".xml")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".properties");
    }

    public static String languageOf(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".vue")) {
            return "vue";
        }
        if (name.endsWith(".js") || name.endsWith(".ts")) {
            return "javascript";
        }
        if (name.endsWith(".xml")) {
            return "xml";
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")) {
            return "config";
        }
        return "unsupported";
    }
}
