package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.cumba.bootstrap.itmain.TestMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The corej-cli / corej-rest guarantee: a config without {@code [bootstrap] single-instance} loads
 * no class of the instance package and no Unix-domain socket class. Proven in a <b>forked</b> JVM
 * with {@code -Xlog:class+load} - an in-JVM probe cannot tell what other tests loaded already. The
 * log must name {@code Launcher} (a blind log would pass otherwise).
 */
class NoKeyClassLoadTest
{

    @TempDir
    Path tmp;

    @Test
    void aConfigWithoutTheKeyLoadsNoInstanceOrSocketClass() throws Exception
    {
        Path out = tmp.resolve("out.txt");
        Path conf = tmp.resolve("app.conf");
        Files.writeString(
                conf, String
                        .join("\n", "[bootstrap]", "main-class = " + TestMain.class.getName(),
                                "[properties]", "launcher.test.out = " + out, "[classpath]",
                                System.getProperty("java.class.path")
                                        .replace(java.io.File.pathSeparator, "\n"),
                                ""),
                StandardCharsets.UTF_8);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(java, "-Xlog:class+load", "-Djava.awt.headless=true",
                "-Duser.home=" + System.getProperty("user.home"), "-Dbootstrap.config=" + conf,
                "-cp", System.getProperty("java.class.path"), Bootstrap.class.getName(), "x.csv")
                        .redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), log);
        assertEquals("args=x.csv", Files.readAllLines(out).get(0), "the target ran");
        List<String> loaded = log.lines().filter(l -> l.contains("[class,load]")).toList();
        assertTrue(loaded.size() > 100, "a real class-load log: " + loaded.size());
        assertTrue(loaded.stream().anyMatch(l -> l.contains("net.cumba.bootstrap.Launcher ")),
                "the log names the launcher");
        List<String> instance = loaded.stream()
                .filter(l -> l.contains("net.cumba.bootstrap.instance.")).toList();
        assertEquals(List.of(), instance, "no instance class on the no-key path");
        List<String> sockets = loaded.stream().filter(l -> l.contains("sun.nio.ch.UnixDomain"))
                .toList();
        assertEquals(List.of(), sockets, "no Unix-domain socket class on the no-key path");
    }
}
