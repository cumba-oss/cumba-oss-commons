package net.cumba.bootstrap;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.InvalidPathException;
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
     *             if the override is not a parseable path, or if no override is set and the
     *             launcher was not loaded from a jar
     */
    public static Path locate(ProtectionDomain protectionDomain, UnaryOperator<String> sysLookup)
    {
        String override = sysLookup.apply(CONFIG_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank())
        {
            String path = override.strip();
            try
            {
                return Path.of(path);
            }
            catch (InvalidPathException e)
            {
                // Same defect as the one ClasspathBuilder guards against, reached through the
                // other entry point: an operator-supplied string goes straight to a path parser,
                // which rejects a NUL on UNIX and the whole reserved set <>:"|?* on Windows. The
                // unchecked InvalidPathException escapes Bootstrap.main as a stack trace and exit
                // 1, breaking the contract that a launcher-side failure is a 'bootstrap: ...'
                // line and exit 2.
                throw new BootstrapException("-D" + CONFIG_OVERRIDE_PROPERTY
                        + " is not a valid path: '" + path + "' (" + e.getMessage() + ")", e);
            }
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
        catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e)
        {
            // A code source need not be a plain file: a nested/executable-jar loader reports a
            // 'jar:' URL, for which Path.of throws the unchecked FileSystemNotFoundException. Left
            // uncaught it escapes as a stack trace and exit 1, breaking the launcher's contract
            // that every launcher-side failure is a 'bootstrap: ...' line and exit 2.
            throw new BootstrapException("launcher location is not a filesystem path: " + location
                    + "; set -D" + CONFIG_OVERRIDE_PROPERTY + "=<path>", e);
        }
    }
}
