package com.ai4se.runtime.demo.delivery;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Copies a broken sample workspace into a fresh run directory. */
public final class WorkspaceBootstrap {

    private WorkspaceBootstrap() {
    }

    public static File prepareRunWorkspace(File moduleRoot) throws IOException {
        return prepareRunWorkspace(moduleRoot, "first-delivery-workspace", "first-delivery-work");
    }

    public static File prepareRunWorkspace(File moduleRoot, String fixtureDirName, String runDirName)
            throws IOException {
        File source = new File(moduleRoot, fixtureDirName);
        if (!source.isDirectory()) {
            throw new IOException("missing fixture " + fixtureDirName + " under " + moduleRoot);
        }
        File target = new File(moduleRoot, "target/" + runDirName);
        if (target.exists()) {
            deleteRecursive(target.toPath());
        }
        Files.createDirectories(target.toPath());
        copyRecursive(source.toPath(), target.toPath());
        return target.getCanonicalFile();
    }

    public static File resolveDemoModuleRoot() throws IOException {
        File cwd = new File("").getCanonicalFile();
        File direct = new File(cwd, "first-delivery-workspace");
        if (direct.isDirectory()) {
            return cwd;
        }
        File nested = new File(cwd, "ai4se-demo/first-delivery-workspace");
        if (nested.isDirectory()) {
            return new File(cwd, "ai4se-demo").getCanonicalFile();
        }
        File stress = new File(cwd, "stress-workspaces");
        if (stress.isDirectory()) {
            return cwd;
        }
        File stressNested = new File(cwd, "ai4se-demo/stress-workspaces");
        if (stressNested.isDirectory()) {
            return new File(cwd, "ai4se-demo").getCanonicalFile();
        }
        File pilot = new File(cwd, "pilot-workspace");
        if (pilot.isDirectory()) {
            return cwd;
        }
        File pilotNested = new File(cwd, "ai4se-demo/pilot-workspace");
        if (pilotNested.isDirectory()) {
            return new File(cwd, "ai4se-demo").getCanonicalFile();
        }
        File promo = new File(cwd, "pilot-workspace-promotion");
        if (promo.isDirectory()) {
            return cwd;
        }
        File promoNested = new File(cwd, "ai4se-demo/pilot-workspace-promotion");
        if (promoNested.isDirectory()) {
            return new File(cwd, "ai4se-demo").getCanonicalFile();
        }
        throw new IOException("cannot locate ai4se-demo fixtures from " + cwd);
    }

    private static void copyRecursive(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path dest = target.resolve(relative.toString());
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path dest = target.resolve(relative.toString());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
