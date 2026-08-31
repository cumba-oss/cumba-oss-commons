package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written, dependency-free parser for the bootstrap INI-style config.
 *
 * <p>
 * Grammar:
 *
 * <ul>
 * <li>Blank lines and lines whose first non-blank character is {@code #} or {@code ;} are ignored.
 * <li>{@code [section]} headers switch the active section. Recognised sections are
 * {@code [bootstrap]}, {@code [properties]} and {@code [classpath]} (case-insensitive).
 * <li>In {@code [bootstrap]} and {@code [properties]} each line is {@code key = value}, split on
 * the <em>first</em> {@code =} (so values may contain {@code =}).
 * <li>In {@code [classpath]} each non-blank line is a single classpath entry (not
 * {@code key = value}).
 * </ul>
 *
 * <p>
 * The parser is pure: it takes the already-read lines and returns a {@link BootstrapConfig}, which
 * makes it trivial to unit-test without touching the filesystem.
 */
public final class ConfigParser
{

    private ConfigParser()
    {
    }


    /**
     * Parses config lines into a {@link BootstrapConfig}.
     *
     * @param lines
     *            the file content, one entry per line, in order
     * @param source
     *            the file the lines came from (used only for error messages)
     * @return the parsed config
     * @throws BootstrapException
     *             on any structural problem (malformed header, entry outside a known section,
     *             missing {@code =}, unknown {@code [bootstrap]} key, bad classloader token)
     */
    public static BootstrapConfig parse(List<String> lines, Path source)
    {
        List<Map.Entry<String, String>> properties = new ArrayList<>();
        List<String> classpath = new ArrayList<>();
        String mainClass = null;
        ClassLoaderMode mode = ClassLoaderMode.PARENT_FIRST;
        String section = null;

        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ';')
            {
                continue;
            }
            if (line.charAt(0) == '[')
            {
                section = parseSectionHeader(line, source, i);
                continue;
            }
            switch (section == null ? "" : section)
            {
            case "classpath" -> classpath.add(line);
            case "properties" -> properties.add(splitKeyValue(source, i, line));
            case "bootstrap" ->
            {
                Map.Entry<String, String> kv = splitKeyValue(source, i, line);
                switch (kv.getKey())
                {
                case "main-class" -> mainClass = kv.getValue();
                case "classloader" -> mode = ClassLoaderMode.fromToken(kv.getValue(), source, i);
                default -> throw BootstrapException.at(source, i,
                        "unknown [bootstrap] key '" + kv.getKey() + "'");
                }
            }
            default -> throw BootstrapException.at(source, i,
                    section == null ? "entry before any [section]: " + line
                            : "entry in unknown section [" + section + "]: " + line);
            }
        }
        return new BootstrapConfig(source, Optional.ofNullable(mainClass), mode, properties,
                classpath);
    }


    private static String parseSectionHeader(String line, Path source, int zeroBasedLine)
    {
        if (!line.endsWith("]"))
        {
            throw BootstrapException.at(source, zeroBasedLine, "malformed section header: " + line);
        }
        return line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
    }


    private static Map.Entry<String, String> splitKeyValue(Path source, int zeroBasedLine,
            String line)
    {
        int eq = line.indexOf('=');
        if (eq < 0)
        {
            throw BootstrapException.at(source, zeroBasedLine, "expected 'key = value': " + line);
        }
        String key = line.substring(0, eq).strip();
        String value = line.substring(eq + 1).strip();
        if (key.isEmpty())
        {
            throw BootstrapException.at(source, zeroBasedLine, "empty key in: " + line);
        }
        return Map.entry(key, value);
    }
}
