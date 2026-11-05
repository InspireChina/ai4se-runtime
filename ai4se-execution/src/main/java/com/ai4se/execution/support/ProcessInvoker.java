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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

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
            // Codex exec can otherwise wait forever for an additional stdin block.
            process.getOutputStream().close();

            ExecutorService drains = Executors.newFixedThreadPool(2, new ThreadFactory() {
                private int next;

                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "ai4se-process-drain-" + (++next));
                    thread.setDaemon(true);
                    return thread;
                }
            });
            Future<String> stdoutFuture = drains.submit(streamReader(process.getInputStream()));
            Future<String> stderrFuture = drains.submit(streamReader(process.getErrorStream()));
            long millis = timeout == null ? 600000L : Math.max(1L, timeout.toMillis());
            boolean finished = process.waitFor(millis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                // Give the top-level process a bounded chance to close its pipes.
                process.waitFor(500L, TimeUnit.MILLISECONDS);
                String stdout = futureText(stdoutFuture, 500L);
                String stderr = futureText(stderrFuture, 500L);
                drains.shutdownNow();
                return new ProcessOutcome(-1, stdout, stderr, true);
            }
            String stdout = futureText(stdoutFuture, 2000L);
            String stderr = futureText(stderrFuture, 2000L);
            drains.shutdownNow();
            return new ProcessOutcome(process.exitValue(), stdout, stderr, false);
        }

        private static Callable<String> streamReader(final InputStream in) {
            return new Callable<String>() {
                @Override
                public String call() throws IOException {
                    return readFully(in);
                }
            };
        }

        private static String futureText(Future<String> future, long millis)
                throws IOException, InterruptedException {
            try {
                return future.get(millis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                return "";
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("stream drain failed", cause);
            }
        }

        private static String readFully(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    buf.write(chunk, 0, n);
                }
            } catch (IOException closedPipe) {
                // A timeout can close a pipe while the drain is between reads. The
                // bytes already captured are still valid evidence and must not be
                // discarded or turn a controlled timeout into an adapter error.
            }
            return new String(buf.toByteArray(), Charset.defaultCharset());
        }
    }
}
