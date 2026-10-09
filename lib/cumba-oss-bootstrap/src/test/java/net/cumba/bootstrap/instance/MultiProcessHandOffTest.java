package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import net.cumba.bootstrap.Bootstrap;
import net.cumba.bootstrap.itmain.StandInServer;
import net.cumba.bootstrap.itmain.StandInServerMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Five launchers started at once against one scope, through the real {@link Bootstrap} entry point,
 * the real lock and real sockets - with a stale endpoint of a "previous run" pre-planted: exactly
 * one becomes primary, the other four hand their file off and exit 0, every path is delivered
 * exactly once, nobody runs EXTRA. The primary's application is the test-written v1 stand-in server
 * ({@link StandInServerMain}); this module has no application half, so the test pins the client,
 * the lock and the endpoint delete.
 *
 * <p>
 * Named {@code *Test}: this module runs no failsafe and surefire's includes do not match
 * {@code *IT}. Excluded from pitest ({@code pitest.excludedTestClasses}): five forked JVMs per
 * mutant would only time out. Every forked JVM gets {@code -Duser.home} explicitly: a child
 * inherits no surefire system property.
 */
class MultiProcessHandOffTest
{

    private static final int STARTS = 5;

    @TempDir
    Path tmp;

    @Test
    void fiveSimultaneousStartsAreOnePrimaryAndFourHandOffs() throws Exception
    {
        Path scope = tmp.resolve("settings");
        Path instanceDir = scope.resolve(Endpoint.DIR_NAME);
        Files.createDirectories(instanceDir);
        // H1: the previous run's endpoint - a socket nobody listens on, a token nobody knows
        StandInServer.writeEndpoint(instanceDir, instanceDir.resolve("s-previous-run"),
                "0123456789abcdef".repeat(4));
        Path results = tmp.resolve("results.txt");
        Path conf = tmp.resolve("app.conf");
        Files.writeString(
                conf, String
                        .join("\n", "[bootstrap]",
                                "main-class = " + StandInServerMain.class.getName(),
                                "single-instance = " + scope, "[classpath]",
                                System.getProperty("java.class.path")
                                        .replace(java.io.File.pathSeparator, "\n"),
                                ""),
                StandardCharsets.UTF_8);
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < STARTS; i++)
        {
            files.add(Files.writeString(tmp.resolve("file" + i + ".csv"), "x"));
        }

        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < STARTS; i++)
        {
            processes.add(start(conf, results, files.get(i)));
        }
        List<String> outputs = new ArrayList<>();
        for (Process p : processes)
        {
            assertTrue(p.waitFor(90, TimeUnit.SECONDS), "a start hung");
            outputs.add(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        int primaries = 0;
        for (int i = 0; i < STARTS; i++)
        {
            String out = outputs.get(i);
            assertEquals(0, processes.get(i).exitValue(),
                    "start " + i + " exit code; output:\n" + out);
            if (out.contains("OUTCOME PRIMARY"))
            {
                primaries++;
                assertTrue(out.contains("DONE"), out);
            }
            else
            {
                assertEquals("", out.strip(), "a handed-off start prints nothing (no app ran)");
            }
        }
        assertEquals(1, primaries, "exactly one primary");
        List<String> delivered = Files.readAllLines(results, StandardCharsets.UTF_8);
        TreeSet<String> expected = new TreeSet<>();
        for (Path f : files)
        {
            expected.add(f.toString());
        }
        assertEquals(expected, new TreeSet<>(delivered), "every path delivered");
        assertEquals(STARTS, delivered.size(), "each exactly once");
        assertTrue(Files.isRegularFile(instanceDir.resolve(Endpoint.LOCK_FILE_NAME)));
        List<Path> sockets;
        try (var s = Files.list(instanceDir))
        {
            sockets = s.filter(p -> p.getFileName().toString().startsWith("s-")).toList();
        }
        assertEquals(
                List.of(), sockets.stream()
                        .filter(p -> p.getFileName().toString().equals("s-previous-run")).toList(),
                "no socket of the previous run was created");
    }


    private Process start(Path aConf, Path aResults, Path aFile) throws IOException
    {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java,
                "-Duser.home=" + System.getProperty("user.home"), "-Djava.awt.headless=true",
                "-Dbootstrap.config=" + aConf,
                "-D" + StandInServerMain.RESULTS_PROPERTY + "=" + aResults,
                "-D" + StandInServerMain.EXPECTED_PROPERTY + "=" + STARTS, "-cp",
                System.getProperty("java.class.path"), Bootstrap.class.getName(), aFile.toString());
        pb.directory(tmp.toFile());
        pb.redirectErrorStream(false);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }
}
