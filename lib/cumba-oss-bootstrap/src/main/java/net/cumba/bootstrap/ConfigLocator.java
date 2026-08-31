package net.cumba.bootstrap;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.function.UnaryOperator;

/**
 * Locates the sidecar config file for the running launcher.
 *
 * <p>
 * Resolution order:
 *
 * <ol>
 * <li>the {@code -Dbootstrap.config=<path>} system property, when set;
 * <li>otherwise, the jar the launcher was loaded from ({@code foo.jar}) with its extension swapped
 * for {@code .conf} ({@code foo.conf}), in the same directory.
 * </ol>
 *
 * <p>
 * When the launcher runs from an exploded directory rather than a jar (e.g. in an IDE or test run),
 * there is no jar to derive a name from, so {@code -Dbootstrap.config} must be supplied.
 */
public final class ConfigLocator
{

    /** System property that overrides the sidecar config location. */
    public static final String CONFIG_OVERRIDE_PROPERTY = "bootstrap.config";

    private static final String JAR_SUFFIX = ".jar";

    private static final String CONFIG_SUFFIX = ".conf";

    private ConfigLocator()
    {
    }


    /**
     * Determines the config file path.
     *
     * @param protectionDomain
     *            the launcher class's protection domain (its code source locates the jar)
     * @param sysLookup
     *            maps a system property name to its value (or {@code null})
     * @return the resolved config file path (existence is not checked here)
     * @throws BootstrapException
     *             if no override is set and the launcher was not loaded from a jar
     */
    public static Path locate(ProtectionDomain protectionDomain, UnaryOperator<String> sysLookup)
    {
        String override = sysLookup.apply(CONFIG_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank())
        {
            return Path.of(override.strip());
        }

        Path jar = jarLocation(protectionDomain);
        Path fileNamePath = jar.getFileName();
        if (fileNamePath == null)
        {
            throw new BootstrapException("cannot derive a config name from launcher location " + jar
                    + "; set -D" + CONFIG_OVERRIDE_PROPERTY + "=<path>");
        }
        String fileName = fileNamePath.toString();
        if (!fileName.endsWith(JAR_SUFFIX))
        {
            throw new BootstrapException("bootstrap was not loaded from a .jar (" + jar
                    + "); set -D" + CONFIG_OVERRIDE_PROPERTY + "=<path> to point at a config file");
        }
        String base = fileName.substring(0, fileName.length() - JAR_SUFFIX.length());
        return jar.resolveSibling(base + CONFIG_SUFFIX);
    }


    private static Path jarLocation(ProtectionDomain protectionDomain)
    {
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null)
        {
            throw new BootstrapException("cannot locate the launcher jar (no code source); set -D"
                    + CONFIG_OVERRIDE_PROPERTY + "=<path>");
        }
        URL location = codeSource.getLocation();
        try
        {
            return Path.of(location.toURI());
        }
        catch (URISyntaxException e)
        {
            throw new BootstrapException("invalid launcher location: " + location, e);
        }
    }
}
