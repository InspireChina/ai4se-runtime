package com.example.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Minimal CSV import CLI — dry-run flag missing on purpose. */
public final class ImportCli {

    private boolean wrote;

    public static void main(String[] args) throws IOException {
        ImportCli cli = new ImportCli();
        System.out.println(cli.run(args));
    }

    public String run(String[] args) throws IOException {
        wrote = false;
        if (args == null || args.length < 1) {
            return "usage: ImportCli <csv-path>";
        }
        Path csv = Paths.get(args[0]);
        List<String> rows = CsvReader.readDataRows(csv);
        // Always writes today — callers that need preview have no switch.
        wrote = true;
        return "imported=" + rows.size();
    }

    public boolean wrote() {
        return wrote;
    }
}
