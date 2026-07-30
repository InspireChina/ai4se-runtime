package com.example.batch;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CsvReader {

    private CsvReader() {
    }

    public static List<String> readDataRows(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, Charset.forName("UTF-8"));
        List<String> rows = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && line.toLowerCase().startsWith("id,")) {
                continue;
            }
            rows.add(line);
        }
        return rows;
    }
}
