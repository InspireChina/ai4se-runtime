package com.example.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ImportCliTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void importsRows() throws Exception {
        File csv = tmp.newFile("sample.csv");
        Files.write(csv.toPath(), "id,name\n1,a\n2,b\n".getBytes(Charset.forName("UTF-8")));
        ImportCli cli = new ImportCli();
        assertEquals("imported=2", cli.run(new String[] {csv.getAbsolutePath()}));
        assertTrue(cli.wrote());
    }
}
