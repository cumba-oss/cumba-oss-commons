package net.cumba.bootstrap.instance;

import java.util.Locale;

/**
 * The operating system family, as far as the hand-off cares: the foreground grant exists on Windows
 * only, the socket-directory fallback on Linux only. Injected, so every branch runs on a Linux
 * build host.
 */
public enum Platform
{

    /** Microsoft Windows. */
    WINDOWS,

    /** Apple macOS. */
    MACOS,

    /** Linux. */
    LINUX,

    /** Anything else. */
    OTHER;

    /**
     * The platform the JVM runs on, from {@code os.name}.
     *
     * @return the platform
     */
    public static Platform current()
    {
        return fromOsName(System.getProperty("os.name", ""));
    }


    /**
     * The platform an {@code os.name} value names.
     *
     * @param aOsName
     *            the value of {@code os.name}
     * @return the platform
     */
    public static Platform fromOsName(String aOsName)
    {
        String name = aOsName.toLowerCase(Locale.ROOT);
        if (name.contains("windows"))
        {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin"))
        {
            return MACOS;
        }
        if (name.contains("linux"))
        {
            return LINUX;
        }
        return OTHER;
    }
}
