package net.cumba.bootstrap;

import java.util.Locale;

/**
 * What the {@link ClasspathBuilder} does when a classpath entry does not exist or a glob matches
 * nothing. Selected by the {@code classpath.error.mode} system property (default {@link #WARN}).
 */
public enum ErrorMode
{

    /** Print a warning to stderr and skip the entry. */
    WARN,

    /** Fail fast with a {@link BootstrapException}. */
    ERROR;

    /**
     * Parses the {@code classpath.error.mode} system property value.
     *
     * @param value
     *            the raw property value (may be {@code null} / blank)
     * @return {@link #WARN} when unset/blank, otherwise the matching mode
     * @throws BootstrapException
     *             if the value is neither {@code WARN} nor {@code ERROR}
     */
    public static ErrorMode fromProperty(String value)
    {
        if (value == null || value.isBlank())
        {
            return WARN;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT))
        {
        case "warn" -> WARN;
        case "error" -> ERROR;
        default -> throw new BootstrapException("invalid " + Launcher.CLASSPATH_ERROR_MODE_PROPERTY
                + " '" + value + "' (expected WARN or ERROR)");
        };
    }
}
