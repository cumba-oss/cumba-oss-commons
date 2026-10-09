package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
}
