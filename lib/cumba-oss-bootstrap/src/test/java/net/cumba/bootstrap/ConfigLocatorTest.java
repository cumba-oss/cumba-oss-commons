package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ConfigLocatorTest
{

    private static String noProps(String key)
    {
        return null;
    }


    private static ProtectionDomain domainAt(String locationUrl) throws MalformedURLException
    {
        if (locationUrl == null)
        {
            return new ProtectionDomain(null, null);
        }
        URL location = URI.create(locationUrl).toURL();
        CodeSource codeSource = new CodeSource(location, (Certificate[]) null);
        return new ProtectionDomain(codeSource, null);
    }


    private static UnaryOperator<String> propsWith(String key, String value)
    {
        Map<String, String> map = Map.of(key, value);
        return map::get;
    }


    @Test
    void overrideWins() throws MalformedURLException
    {
        UnaryOperator<String> sys = propsWith(ConfigLocator.CONFIG_OVERRIDE_PROPERTY,
                "/etc/x.conf");
        Path located = ConfigLocator.locate(domainAt("file:/opt/app/foo.jar"), sys);
        assertEquals(Path.of("/etc/x.conf"), located);
    }


    @Test
    void blankOverrideIsIgnored() throws MalformedURLException
    {
        UnaryOperator<String> sys = propsWith(ConfigLocator.CONFIG_OVERRIDE_PROPERTY, "  ");
        Path located = ConfigLocator.locate(domainAt("file:/opt/app/foo.jar"), sys);
        assertEquals(Path.of("/opt/app/foo.conf"), located);
    }


    @Test
    void derivesSiblingConfFromJar() throws MalformedURLException
    {
        Path located = ConfigLocator.locate(domainAt("file:/opt/app/net.cumba.bootstrap.jar"),
                ConfigLocatorTest::noProps);
        assertEquals(Path.of("/opt/app/net.cumba.bootstrap.conf"), located);
    }


    @Test
    void nonJarLocationFails() throws MalformedURLException
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> ConfigLocator
                .locate(domainAt("file:/opt/app/classes/"), ConfigLocatorTest::noProps));
        assertTrue(ex.getMessage().contains("not loaded from a .jar"));
    }


    @Test
    void nullCodeSourceFails() throws MalformedURLException
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigLocator.locate(domainAt(null), ConfigLocatorTest::noProps));
        assertTrue(ex.getMessage().contains("cannot locate the launcher jar"));
    }


    @Test
    void nullLocationFails()
    {
        CodeSource codeSource = new CodeSource(null, (Certificate[]) null);
        ProtectionDomain domain = new ProtectionDomain(codeSource, null);
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigLocator.locate(domain, ConfigLocatorTest::noProps));
        assertTrue(ex.getMessage().contains("cannot locate the launcher jar"));
    }


    @Test
    void nonFilesystemCodeSourceFailsWithTheUsualGuidance() throws MalformedURLException
    {
        // A nested-jar loader reports a 'jar:' code source. Path.of then throws the UNCHECKED
        // FileSystemNotFoundException, which is not a BootstrapException — so without the catch it
        // escapes Bootstrap.main as a stack trace and exit 1 instead of 'bootstrap: ...' / exit 2.
        BootstrapException ex = assertThrows(BootstrapException.class, () -> ConfigLocator
                .locate(domainAt("jar:file:/opt/app/foo.jar!/"), ConfigLocatorTest::noProps));
        assertTrue(ex.getMessage().contains("not a filesystem path"), ex::getMessage);
        assertTrue(ex.getMessage().contains(ConfigLocator.CONFIG_OVERRIDE_PROPERTY),
                ex::getMessage);
    }


    @Test
    void anUnparseableOverrideFailsAsABootstrapError() throws MalformedURLException
    {
        // The same defect ClasspathBuilder guards against, reached through -Dbootstrap.config
        // instead of a [classpath] line: an operator string handed straight to a path parser.
        // A NUL is the only character the UNIX parser rejects; on Windows the reserved set is
        // <>:"|?* and this case is reachable for an ordinary typo. Either way the unchecked
        // InvalidPathException must not escape Bootstrap.main as a stack trace and exit 1.
        UnaryOperator<String> sys = propsWith(ConfigLocator.CONFIG_OVERRIDE_PROPERTY,
                "/etc/x" + (char) 0 + ".conf");
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigLocator.locate(domainAt("file:/opt/app/foo.jar"), sys));
        assertTrue(ex.getMessage().contains(ConfigLocator.CONFIG_OVERRIDE_PROPERTY),
                ex::getMessage);
        assertTrue(ex.getMessage().contains("not a valid path"), ex::getMessage);
        assertEquals(InvalidPathException.class, ex.getCause().getClass());
    }
}
