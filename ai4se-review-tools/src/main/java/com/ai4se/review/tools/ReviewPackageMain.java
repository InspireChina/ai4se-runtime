package com.ai4se.review.tools;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry: {@code ReviewPackageMain [--repo-root <path>] [--package-id <id>]}.
 */
public final class ReviewPackageMain {

    private ReviewPackageMain() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = ReviewPackageGenerator.detectRepoRoot(Paths.get(".").toAbsolutePath());
        String packageId = null;

        for (int i = 0; i < args.length; i++) {
            if ("--repo-root".equals(args[i]) && i + 1 < args.length) {
                repoRoot = Paths.get(args[++i]).toAbsolutePath().normalize();
            } else if ("--package-id".equals(args[i]) && i + 1 < args.length) {
                packageId = args[++i];
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                printHelp();
                return;
            }
        }

        ReviewPackageGenerator generator = new ReviewPackageGenerator(repoRoot);
        Path out = packageId == null ? generator.generate() : generator.generate(packageId);
        System.out.println("ReviewPackage generated: " + out);
    }

    private static void printHelp() {
        System.out.println("Usage: ReviewPackageMain [--repo-root <path>] [--package-id <id>]");
        System.out.println("Writes review-package/generated/<packageId>/ per SCHEMA.md v1.0");
    }
}
