package net.cumba.bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Expands resolved {@code [classpath]} entries into a de-duplicated array of {@link URL}s.
 *
 * <p>
 * Behaviour:
 *
 * <ul>
 * <li>Relative entries resolve against {@code baseDir} (the config file's directory) so configs are
 * location-independent.
 * <li>Entries containing {@code *} or {@code ?} are glob-expanded: the entry is split <em>as a
 * string</em> at its first glob-bearing segment into a concrete base directory plus a {@code glob:}
 * pattern, the base is walked, and matches are sorted lexicographically for determinism. {@code **}
 * matches across directories; {@code *}/{@code ?} match within a single segment.
 * <li>Globs are portable: the split never hands a glob character to a path parser (Windows rejects
 * {@code *} and {@code ?} outright), and the pattern is always written with {@code '/'}, which
 * {@code java.nio} maps onto the platform separator. Both halves are load-bearing on Windows and
 * neither is observable on a UNIX build host — see {@code WindowsClasspathGlobTest}, which runs the
 * whole class against an in-memory Windows filesystem.
 * <li>Results are de-duplicated by <em>canonical</em> path (first occurrence wins, order
 * preserved), so the same jar is never added to the classpath twice.
 * <li>A non-existent plain entry, or a glob that matches nothing, is routed through the configured
 * {@link ErrorMode} ({@link ErrorMode#WARN} skips, {@link ErrorMode#ERROR} throws).
 * </ul>
 */
public final class ClasspathBuilder
{

    private final Path baseDir;

    private final ErrorMode errorMode;

    private final Consumer<String> warn;

    /**
     * Creates a builder that prints warnings to {@link System#err}.
     *
     * @param baseDir
     *            directory relative entries resolve against
     * @param errorMode
     *            policy for missing entries / empty globs
     */
    public ClasspathBuilder(Path baseDir, ErrorMode errorMode)
    {
        this(baseDir, errorMode, System.err::println);
    }


    /**
     * Creates a builder with an injectable warning sink (for testing).
     *
     * @param baseDir
     *            directory relative entries resolve against
     * @param errorMode
     *            policy for missing entries / empty globs
     * @param warn
     *            sink for warning messages
     */
    public ClasspathBuilder(Path baseDir, ErrorMode errorMode, Consumer<String> warn)
    {
        this.baseDir = baseDir;
        this.errorMode = errorMode;
        this.warn = warn;
    }


    /**
     * Expands the given entries into classpath URLs.
     *
     * @param entries
     *            resolved (post-interpolation) classpath entries, in order
     * @return de-duplicated classpath URLs, preserving first-occurrence order
     * @throws BootstrapException
     *             on an I/O failure or a missing entry under {@link ErrorMode#ERROR}
     */
    public URL[] build(List<String> entries)
    {
        Set<Path> canonical = new LinkedHashSet<>();
        for (String entry : entries)
        {
            if (entry.isBlank())
            {
                handleEmpty(entry);
            }
            else if (isGlob(entry))
            {
                expandGlob(entry, canonical);
            }
            else
            {
                expandPlain(entry, canonical);
            }
        }
        List<URL> urls = new ArrayList<>(canonical.size());
        for (Path path : canonical)
        {
            urls.add(toUrl(path));
        }
        return urls.toArray(URL[]::new);
    }


    private void expandPlain(String entry, Set<Path> out)
    {
        Path path = baseDir.resolve(toPath(entry, entry));
        if (Files.exists(path))
        {
            out.add(canonicalize(path));
        }
        else
        {
            handleMissing(entry);
        }
    }


    private void expandGlob(String entry, Set<Path> out)
    {
        // Split as a STRING, never via Path.of. Windows accepts '/' as a separator but its path
        // parser rejects '*' and '?' OUTRIGHT (sun.nio.fs.WindowsPathParser's reserved set is
        // <>:"|?*), so parsing the whole entry first throws InvalidPathException before any
        // matching is attempted -- and an unchecked exception escaping here leaves Bootstrap.main
        // with a stack trace and exit 1 instead of the 'bootstrap: ...' line and exit 2 the
        // launcher contracts for. The README's own canonical '${app.home}/lib/*.jar' hit exactly
        // that on every Windows install.
        String separators = separators();
        int globStart = globSegmentStart(entry, separators);
        String concrete = entry.substring(0, globStart);
        Path searchRoot = concrete.isEmpty() ? baseDir : baseDir.resolve(toPath(concrete, entry));
        PathMatcher matcher = globMatcher(entry.substring(globStart), separators, entry);

        List<Path> matches = new ArrayList<>();
        if (Files.isDirectory(searchRoot))
        {
            try (Stream<Path> walk = Files.walk(searchRoot))
            {
                walk.filter(p -> !p.equals(searchRoot))
                        .filter(p -> matcher.matches(searchRoot.relativize(p)))
                        .forEach(matches::add);
            }
            catch (IOException | UncheckedIOException e)
            {
                // Files.walk reports a directory it cannot read by throwing UncheckedIOException
                // from the terminal operation, not the checked IOException from the factory. Left
                // uncaught it escapes Bootstrap.main as a stack trace and exit 1, breaking the
                // contract that a launcher-side failure is a 'bootstrap: ...' line and exit 2.
                throw new BootstrapException(
                        "failed to expand classpath glob '" + entry + "': " + e.getMessage(), e);
            }
        }

        if (matches.isEmpty())
        {
            handleMissing(entry);
            return;
        }
        matches.sort(Comparator.comparing(Path::toString));
        for (Path match : matches)
        {
            out.add(canonicalize(match));
        }
    }


    /**
     * Characters that separate path segments on {@code baseDir}'s filesystem. {@code '/'} is always
     * one: it is the separator on UNIX and an accepted alternative on Windows, so a config written
     * with forward slashes is portable. Appending it unconditionally keeps this branch-free (on
     * UNIX the result is a harmless {@code "//"}).
     *
     * @return the separator characters, as a set to be probed with {@link String#indexOf(int)}
     */
    private String separators()
    {
        return baseDir.getFileSystem().getSeparator() + "/";
    }


    /**
     * Builds the {@code glob:} matcher for the glob-bearing tail of an entry.
     *
     * <p>
     * ⚠ The pattern is written with {@code '/'} on <em>every</em> platform, whatever the entry
     * used. {@code sun.nio.fs.Globs} compiles a {@code '/'} in a Windows glob into the {@code '\'}
     * separator, while a literal {@code '\'} there is the glob <em>escape</em> character — so
     * joining the segments with the platform separator would turn {@code lib\*.jar} into "the
     * literal name {@code *.jar}" and match nothing at all, silently.
     *
     * @param globPart
     *            the tail of the entry from its first glob-bearing segment onwards
     * @param separators
     *            the filesystem's separator characters
     * @param entry
     *            the whole entry, for the error message
     * @return a matcher for paths relative to the search root
     */
    private PathMatcher globMatcher(String globPart, String separators, String entry)
    {
        StringBuilder pattern = new StringBuilder(globPart.length());
        for (int i = 0; i < globPart.length(); i++)
        {
            char c = globPart.charAt(i);
            pattern.append(separators.indexOf(c) >= 0 ? '/' : c);
        }
        try
        {
            return baseDir.getFileSystem().getPathMatcher("glob:" + pattern);
        }
        catch (PatternSyntaxException e)
        {
            // An unbalanced '[' or '{' in an operator-written entry: a configuration mistake, and
            // so a 'bootstrap: ...' line and exit 2, not an unchecked exception and exit 1.
            throw new BootstrapException(
                    "malformed classpath glob '" + entry + "': " + e.getMessage(), e);
        }
    }


    /**
     * Parses a glob-free path fragment on {@code baseDir}'s filesystem.
     *
     * @param text
     *            the fragment to parse
     * @param entry
     *            the whole entry, for the error message
     * @return the parsed path
     */
    private Path toPath(String text, String entry)
    {
        try
        {
            return baseDir.getFileSystem().getPath(text);
        }
        catch (InvalidPathException e)
        {
            // Windows rejects <>:"| in a path as well as the glob characters handled above. Same
            // reasoning as globMatcher: an operator's typo must not become a stack trace.
            throw new BootstrapException(
                    "invalid classpath entry '" + entry + "': " + e.getMessage(), e);
        }
    }


    /**
     * Finds where the first glob-bearing <em>segment</em> of an entry starts, working on the raw
     * string so that no glob character is ever handed to a path parser.
     *
     * @param entry
     *            the entry, known to contain a glob character
     * @param separators
     *            the filesystem's separator characters
     * @return the index in {@code entry} at which that segment starts
     */
    private static int globSegmentStart(String entry, String separators)
    {
        int segmentStart = 0;
        for (int i = 0; i < entry.length(); i++)
        {
            char c = entry.charAt(i);
            if (separators.indexOf(c) >= 0)
            {
                segmentStart = i + 1;
            }
            else if (isGlobChar(c))
            {
                return segmentStart;
            }
        }
        // Unreachable: only called for entries already known to contain a glob, so the loop
        // always returns above. That precondition also makes the loop bound an EQUIVALENT
        // mutation target (i < length vs i <= length): the bound is never reached.
        return 0;
    }


    /**
     * Routes a classpath entry that interpolated to nothing through the configured
     * {@link ErrorMode}.
     *
     * <p>
     * This case cannot be folded into {@link #expandPlain}: {@code Path.resolve("")} returns the
     * base path itself, so a blank entry would pass the {@code Files.exists} check and put the
     * <em>config directory</em> on the classpath — silently, in place of the jars the operator
     * meant to add, and without even {@link ErrorMode#ERROR} noticing, because nothing is missing.
     * A line that resolves to nothing is a configuration problem, not a path.
     *
     * @param entry
     *            the resolved (blank) entry, echoed back so the operator can see which line
     *            produced it
     */
    private void handleEmpty(String entry)
    {
        String message = "classpath entry resolved to an empty path: '" + entry + "'";
        if (errorMode == ErrorMode.ERROR)
        {
            throw new BootstrapException(message);
        }
        warn.accept("bootstrap: WARNING: " + message + " (skipped)");
    }


    private void handleMissing(String entry)
    {
        String message = "classpath entry not found / matched nothing: " + entry;
        if (errorMode == ErrorMode.ERROR)
        {
            throw new BootstrapException(message);
        }
        warn.accept("bootstrap: WARNING: " + message + " (skipped)");
    }


    private static boolean isGlob(String text)
    {
        return text.indexOf('*') >= 0 || text.indexOf('?') >= 0;
    }


    private static boolean isGlobChar(char c)
    {
        return c == '*' || c == '?';
    }


    private static Path canonicalize(Path path)
    {
        try
        {
            return path.toRealPath();
        }
        catch (IOException e)
        {
            return path.toAbsolutePath().normalize();
        }
    }


    private static URL toUrl(Path path)
    {
        try
        {
            return path.toUri().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new BootstrapException("cannot convert to URL: " + path, e);
        }
    }
}
