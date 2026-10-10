package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.cumba.bootstrap.instance.Clock;
import net.cumba.bootstrap.instance.CloseWatchdog;
import net.cumba.bootstrap.instance.DirCheck;
import net.cumba.bootstrap.instance.Endpoint;
import net.cumba.bootstrap.instance.ForegroundGrant;
import net.cumba.bootstrap.instance.InstanceGate;
import net.cumba.bootstrap.instance.LockProbe;
import net.cumba.bootstrap.instance.Platform;
import org.junit.jupiter.api.Test;

/**
 * The module's tests never reach the real home: the pom redirects {@code user.home} to
 * {@code target/test-home} in surefire and in the pitest minions, and passes the real one as
 * {@code realUserHome}. The single-instance tests create {@code instance/} directories and lock
 * files under the scope they are given; this guard is what reds if the redirect is ever lost.
 */
class TestHomeRedirectTest
{

    @Test
    void userHomeIsRedirectedUnderTarget()
    {
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath();
        assertTrue(home.endsWith(Path.of("target", "test-home")), "user.home=" + home);
        String real = System.getProperty("realUserHome");
        assertNotNull(real, "realUserHome is passed by the pom");
        assertFalse(real.isBlank());
        assertNotEquals(Path.of(real).toAbsolutePath(), home, "the redirect is not the real home");
    }


    @Test
    void theShippedScopeLandsInTheRedirectAndTheRealHomeGainsNoInstanceDirectory()
        throws IOException
    {
        // plan review M7: the scope as the data browser's databrowser.conf spells it, through the
        // real gate (directory check, endpoint delete); only the lock is a stand-in, so no lock
        // is held for the JVM's life and no connect window can run
        Path realInstance = Path.of(System.getProperty("realUserHome")).resolve(".cumbaDataBrowser")
                .resolve(Endpoint.DIR_NAME);
        Map<Path, String> before = snapshot(realInstance);
        String scope = new VariableResolver(System::getenv, System::getProperty,
                new LinkedHashMap<>()).resolve("${sys:user.home}/.cumbaDataBrowser",
                        Path.of("databrowser.conf"));
        InstanceGate.Outcome o = InstanceGate.enter(Path.of(scope), new String[0],
                Path.of(System.getProperty("user.dir")),
                new InstanceGate.Deps(Clock.SYSTEM, aLockFile -> LockProbe.Status.ACQUIRED,
                        DirCheck.DEFAULT, Platform.current(), ForegroundGrant.NONE,
                        CloseWatchdog.daemon(), () ->
                        {
                        }));
        assertEquals(before, snapshot(realInstance),
                "the real home's .cumbaDataBrowser/instance gained or changed nothing");
        assertEquals(InstanceGate.Kind.PRIMARY, o.kind(), o.toString());
        Path redirected = Path.of(System.getProperty("user.home")).toAbsolutePath()
                .resolve(".cumbaDataBrowser").resolve(Endpoint.DIR_NAME);
        assertEquals(redirected, o.instanceDir().toAbsolutePath());
        assertTrue(Files.isDirectory(redirected), "created under the redirect instead");
    }


    /** Every entry under {@code aDir}, recursively, with a change stamp; empty when absent. */
    private static Map<Path, String> snapshot(Path aDir) throws IOException
    {
        Map<Path, String> state = new TreeMap<>();
        if (!Files.exists(aDir, LinkOption.NOFOLLOW_LINKS))
        {
            return state;
        }
        try (Stream<Path> walk = Files.walk(aDir))
        {
            for (Path p : walk.toList())
            {
                state.put(p,
                        Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) ? "dir"
                                : Files.size(p) + "@"
                                        + Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS));
            }
        }
        return state;
    }
}
