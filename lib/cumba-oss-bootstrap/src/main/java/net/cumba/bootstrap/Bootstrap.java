package net.cumba.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Entry point of the launcher jar. Locates and parses the sidecar config, then delegates to
 * {@link Launcher} to apply it and invoke the target program.
 *
 * <p>
 * A launcher-side failure ({@link BootstrapException}) prints a concise {@code bootstrap: ...}
 * message to stderr and exits with status {@code 2}. Exceptions thrown by the target program itself
 * propagate unchanged so the target controls its own exit status.
 */
public final class Bootstrap
{

    private Bootstrap()
    {
    }


    /**
     * Launcher entry point.
     *
     * @param args
     *            command-line arguments, passed through verbatim to the target {@code main}
     * @throws Exception
     *             any exception thrown by the target program (propagated as-is)
     */
    public static void main(String[] args) throws Exception
    {
        try
        {
            run(args, Bootstrap.class.getProtectionDomain(), System::getProperty);
        }
        catch (BootstrapException e)
        {
            System.err.println("bootstrap: " + e.getMessage());
            System.exit(2);
        }
    }


    /**
     * Locates, reads and applies the config, then launches the target. Throws rather than exiting,
     * so it is unit-testable; {@link #main(String[])} maps {@link BootstrapException} to an exit
     * code.
     *
     * @param args
     *            command-line arguments for the target {@code main}
     * @param protectionDomain
     *            the launcher's protection domain (config location + manifest fallback)
     * @param sysLookup
     *            maps a system property name to its value (or {@code null})
     * @throws Exception
     *             a {@link BootstrapException} on any launcher-side failure, or any exception
     *             thrown by the target program
     */
    static void run(String[] args, ProtectionDomain protectionDomain,
            UnaryOperator<String> sysLookup)
        throws Exception
    {
        Path configPath = ConfigLocator.locate(protectionDomain, sysLookup);
        List<String> lines = readConfig(configPath);
        BootstrapConfig config = ConfigParser.parse(lines, configPath);
        Launcher.launch(config, args, protectionDomain);
    }


    private static List<String> readConfig(Path configPath)
    {
        try
        {
            return Files.readAllLines(configPath, StandardCharsets.UTF_8);
        }
        catch (NoSuchFileException e)
        {
            throw new BootstrapException("bootstrap config not found: " + configPath
                    + " (override with -D" + ConfigLocator.CONFIG_OVERRIDE_PROPERTY + "=<path>)");
        }
        catch (IOException e)
        {
            throw new BootstrapException(
                    "cannot read bootstrap config " + configPath + ": " + e.getMessage(), e);
        }
    }
}
