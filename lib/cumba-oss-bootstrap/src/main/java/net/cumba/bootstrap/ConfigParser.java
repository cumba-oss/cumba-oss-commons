package net.cumba.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * <li>In {@code [bootstrap]} a repeated key overwrites, except {@code user-config} (each occurrence
 * adds a candidate, in order) and {@code reserved} (each occurrence adds its comma-separated keys).
 * <li>{@code [bootstrap] single-instance =
 *
<dir>
 * } opts the distribution into the single-instance gate
 * ({@link net.cumba.bootstrap.instance.InstanceGate}); the value is the instance scope and may not
 * be empty.
 * </ul>
 *
 * <p>
 * A user overlay ({@link #parseOverlay}) uses the same grammar but may hold only
 * {@code [properties]}.
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
        List<String> userConfigCandidates = new ArrayList<>();
        Set<String> reservedKeys = new LinkedHashSet<>();
        String mainClass = null;
        String singleInstanceDir = null;
        ClassLoaderMode mode = ClassLoaderMode.PARENT_FIRST;
        String section = null;

        for (int i = 0; i < lines.size(); i++)
        {
            String line = (i == 0 ? withoutByteOrderMark(lines.get(i)) : lines.get(i)).strip();
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
                case "user-config" -> userConfigCandidates.add(requireValue(kv, source, i));
                case "reserved" -> reservedKeys
                        .addAll(keyList(requireValue(kv, source, i), source, i));
                case "single-instance" -> singleInstanceDir = requireValue(kv, source, i);
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
                classpath, userConfigCandidates, reservedKeys,
                Optional.ofNullable(singleInstanceDir));
    }


    /**
     * Parses a user overlay: the same grammar as {@link #parse}, but only {@code [properties]} is
     * allowed. The class path, the main class and the class loader are the installation's; a user
     * file must not be able to change what code runs.
     *
     * @param lines
     *            the file content, one entry per line, in order
     * @param source
     *            the overlay file (used only for error messages)
     * @return the ordered {@code [properties]} key/value pairs (pre-interpolation)
     * @throws BootstrapException
     *             on a structural problem, or any section other than {@code [properties]}
     */
    public static List<Map.Entry<String, String>> parseOverlay(List<String> lines, Path source)
    {
        List<Map.Entry<String, String>> properties = new ArrayList<>();
        String section = null;
        for (int i = 0; i < lines.size(); i++)
        {
            String line = (i == 0 ? withoutByteOrderMark(lines.get(i)) : lines.get(i)).strip();
            if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ';')
            {
                continue;
            }
            if (line.charAt(0) == '[')
            {
                section = parseSectionHeader(line, source, i);
                if (!"properties".equals(section))
                {
                    throw BootstrapException.at(source, i, "a user configuration may hold only"
                            + " [properties], not [" + section + "]");
                }
                continue;
            }
            if (section == null)
            {
                throw BootstrapException.at(source, i,
                        "entry before the [properties] header: " + line);
            }
            properties.add(splitKeyValue(source, i, line));
        }
        return properties;
    }


    private static String requireValue(Map.Entry<String, String> kv, Path source, int zeroBasedLine)
    {
        if (kv.getValue().isEmpty())
        {
            throw BootstrapException.at(source, zeroBasedLine,
                    "empty value for [bootstrap] key '" + kv.getKey() + "'");
        }
        return kv.getValue();
    }


    /**
     * The comma-separated keys of a {@code reserved} value, stripped, blanks dropped. A key with
     * whitespace inside is rejected: no property key can contain it, so {@code reserved = a b} (a
     * missing comma) would otherwise reserve nothing, silently.
     */
    private static List<String> keyList(String value, Path source, int zeroBasedLine)
    {
        List<String> keys = new ArrayList<>();
        for (String key : value.split(",", -1))
        {
            String stripped = key.strip();
            if (stripped.chars().anyMatch(Character::isWhitespace))
            {
                throw BootstrapException.at(source, zeroBasedLine,
                        "[bootstrap] reserved takes comma-separated keys, not '" + stripped + "'");
            }
            if (!stripped.isEmpty())
            {
                keys.add(stripped);
            }
        }
        if (keys.isEmpty())
        {
            // 'reserved = ,' would otherwise pass the empty-value check and reserve nothing.
            throw BootstrapException.at(source, zeroBasedLine,
                    "empty value for [bootstrap] key 'reserved'");
        }
        return keys;
    }


    /**
     * The first line without a UTF-8 byte-order mark. Editors such as Windows Notepad ("UTF-8 with
     * BOM") and PowerShell 5.1 ({@code Set-Content -Encoding UTF8}) write one; {@code strip()} does
     * not remove U+FEFF, so it would otherwise turn a comment or section header on line 1 into an
     * entry before any section.
     */
    static String withoutByteOrderMark(String firstLine)
    {
        return !firstLine.isEmpty() && firstLine.charAt(0) == '\uFEFF' ? firstLine.substring(1)
                : firstLine;
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
