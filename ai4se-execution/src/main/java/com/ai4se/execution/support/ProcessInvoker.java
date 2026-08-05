package com.ai4se.execution.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Process runner seam — production uses real OS processes; tests inject fakes. */
public interface ProcessInvoker {

    ProcessOutcome run(
            List<String> argv,
            Path workingDirectory,
            Map<String, String> extraEnv,
            Duration timeout) throws IOException, InterruptedException;

    final class ProcessOutcome {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        public ProcessOutcome(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.timedOut = timedOut;
        }
    }

    final class RealProcessInvoker implements ProcessInvoker {
        @Override
        public ProcessOutcome run(
                List<String> argv,
                Path workingDirectory,
                Map<String, String> extraEnv,
                Duration timeout) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(argv);
            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }
            if (extraEnv != null && !extraEnv.isEmpty()) {
                pb.environment().putAll(extraEnv);
            }
            Process process = pb.start();
            String stdout = readFully(process.getInputStream());
            String stderr = readFully(process.getErrorStream());
            long seconds = Math.max(1L, timeout == null ? 600L : timeout.getSeconds());
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessOutcome(-1, stdout, stderr, true);
            }
            return new ProcessOutcome(process.exitValue(), stdout, stderr, false);
        }

        private static String readFully(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), Charset.defaultCharset());
        }
    }
}
