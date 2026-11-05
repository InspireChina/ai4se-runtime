package com.ai4se.context.packagebuild;

/** Builder FAIL — P1 missing; must not open CLI / Adapter. */
public final class PackageRefuseException extends RuntimeException {

    public PackageRefuseException(String message) {
        super(message);
    }
}
