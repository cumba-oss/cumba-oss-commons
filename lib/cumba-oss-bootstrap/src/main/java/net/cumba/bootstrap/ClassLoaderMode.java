package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Delegation strategy for the {@link java.net.URLClassLoader} the launcher builds over the
 * configured classpath.
 *
 * <ul>
 * <li>{@link #PARENT_FIRST} — standard Java delegation: ask the parent classloader first, fall back
 * to the configured classpath. With the platform classloader as parent (JDK only) this guarantees
 * {@code java.*} classes always win, which is what almost every deployment wants.
 * <li>{@link #CHILD_FIRST} — "parent-last": try the configured classpath first and only delegate to
 * the parent when a class is not found locally. An escape hatch for deployments that must shadow a
 * class also visible on the parent.
 * </ul>
 */
public enum ClassLoaderMode
{

    /** Delegate to the parent before the configured classpath (the default). */
    PARENT_FIRST,

    /** Try the configured classpath before delegating to the parent. */
    CHILD_FIRST;

    /**
     * Parses a {@code [bootstrap] classloader = ...} token.
     *
     * @param token
     *            the raw value (e.g. {@code parent-first} / {@code child-first})
     * @param source
     *            the config file, for error reporting
     * @param zeroBasedLine
     *            the line the token was read from (0-based)
     * @return the matching mode
     * @throws BootstrapException
     *             if the token is not recognised
     */
    public static ClassLoaderMode fromToken(String token, Path source, int zeroBasedLine)
    {
        String normalized = token.strip().toLowerCase(Locale.ROOT);
        return switch (normalized)
        {
        case "parent-first" -> PARENT_FIRST;
        case "child-first" -> CHILD_FIRST;
        default -> throw BootstrapException.at(source, zeroBasedLine,
                "unknown classloader mode '" + token + "' (expected parent-first or child-first)");
        };
    }
}
