package net.cumba.bootstrap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
 * <li>Entries containing {@code *} or {@code ?} are glob-expanded: the entry is split at its first
 * glob-bearing segment into a concrete base directory plus a {@code glob:} pattern, the base is
 * walked, and matches are sorted lexicographically for determinism. {@code **} matches across
 * directories; {@code *}/{@code ?} match within a single segment.
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
            if (isGlob(entry))
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
        Path path = baseDir.resolve(entry);
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
        Path entryPath = Path.of(entry);
        int globIndex = firstGlobSegment(entryPath);

        Path searchRoot = searchRoot(entryPath, globIndex);
        String pattern = entryPath.subpath(globIndex, entryPath.getNameCount()).toString();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Path> matches = new ArrayList<>();
        if (Files.isDirectory(searchRoot))
        {
            try (Stream<Path> walk = Files.walk(searchRoot))
            {
                walk.filter(p -> !p.equals(searchRoot))
                        .filter(p -> matcher.matches(searchRoot.relativize(p)))
                        .forEach(matches::add);
            }
            catch (IOException e)
            {
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


    private Path searchRoot(Path entryPath, int globIndex)
    {
        Path root = entryPath.getRoot();
        if (globIndex == 0)
        {
            return root != null ? root : baseDir;
        }
        Path concrete = entryPath.subpath(0, globIndex);
        return root != null ? root.resolve(concrete) : baseDir.resolve(concrete);
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


    private static int firstGlobSegment(Path entryPath)
    {
        for (int i = 0; i < entryPath.getNameCount(); i++)
        {
            if (isGlob(entryPath.getName(i).toString()))
            {
                return i;
            }
        }
        // Unreachable: only called for entries already known to contain a glob.
        return 0;
    }


    private static boolean isGlob(String text)
    {
        return text.indexOf('*') >= 0 || text.indexOf('?') >= 0;
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
