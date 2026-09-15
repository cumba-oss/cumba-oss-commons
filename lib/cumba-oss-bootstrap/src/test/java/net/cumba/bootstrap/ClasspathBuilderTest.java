package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathBuilderTest
{

    @TempDir
    Path tmp;

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void layout() throws IOException
    {
        Files.createDirectories(tmp.resolve("lib/sub"));
        Files.createDirectories(tmp.resolve("classes"));
        Files.createFile(tmp.resolve("lib/a.jar"));
        Files.createFile(tmp.resolve("lib/b.jar"));
        Files.createFile(tmp.resolve("lib/sub/c.jar"));
        Files.createFile(tmp.resolve("lib/notes.txt"));
        Files.createFile(tmp.resolve("top.jar"));
    }


    private static List<String> relativeNames(URL[] urls, Path base)
        throws URISyntaxException, IOException
    {
        Path root = base.toRealPath();
        List<String> names = new ArrayList<>();
        for (URL url : urls)
        {
            names.add(root.relativize(Path.of(url.toURI())).toString());
        }
        return names;
    }


    private ClasspathBuilder builder(ErrorMode mode)
    {
        return new ClasspathBuilder(tmp, mode, warnings::add);
    }


    private static List<String> fileNames(URL[] urls) throws URISyntaxException
    {
        List<String> names = new ArrayList<>();
        for (URL url : urls)
        {
            names.add(Path.of(url.toURI()).getFileName().toString());
        }
        return names;
    }


    @Test
    void singleSegmentGlobMatchesOnlyTopLevel() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/*.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void doubleStarMatchesJarsAtAllDepths() throws URISyntaxException
    {
        // '**' crosses directory boundaries, so 'lib/**.jar' matches top-level and nested jars.
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/**.jar"));
        assertEquals(List.of("a.jar", "b.jar", "c.jar"), fileNames(urls));
    }


    @Test
    void doubleStarSlashMatchesNestedJarsOnly() throws URISyntaxException
    {
        // Standard glob: 'lib/**/*.jar' requires at least one directory level, so the top-level
        // a.jar / b.jar do NOT match — only the nested sub/c.jar does.
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/**/*.jar"));
        assertEquals(List.of("c.jar"), fileNames(urls));
    }


    @Test
    void plainDirectoryEntryIsAdded() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("classes"));
        assertEquals(List.of("classes"), fileNames(urls));
    }


    @Test
    void plainJarEntryIsAdded() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/a.jar"));
        assertEquals(List.of("a.jar"), fileNames(urls));
    }


    @Test
    void duplicateAcrossExplicitAndGlobIsAddedOnce() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/a.jar", "lib/*.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void absoluteGlobIsExpanded() throws URISyntaxException
    {
        String absolute = tmp.resolve("lib").toString() + "/*.jar";
        // base dir is irrelevant for an absolute entry.
        ClasspathBuilder builder = new ClasspathBuilder(tmp.resolve("elsewhere"), ErrorMode.WARN,
                warnings::add);
        assertEquals(List.of("a.jar", "b.jar"), fileNames(builder.build(List.of(absolute))));
    }


    @Test
    void missingPlainEntryWarnsAndSkipsUnderWarn()
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/missing.jar"));
        assertEquals(0, urls.length);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("lib/missing.jar"));
    }


    @Test
    void emptyGlobWarnsAndSkipsUnderWarn()
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/*.zip"));
        assertEquals(0, urls.length);
        assertEquals(1, warnings.size());
    }


    @Test
    void missingPlainEntryThrowsUnderError()
    {
        ClasspathBuilder builder = builder(ErrorMode.ERROR);
        List<String> entries = List.of("lib/missing.jar");
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> builder.build(entries));
        assertTrue(ex.getMessage().contains("missing.jar"));
    }


    @Test
    void emptyGlobThrowsUnderError()
    {
        ClasspathBuilder builder = builder(ErrorMode.ERROR);
        List<String> entries = List.of("lib/*.zip");
        assertThrows(BootstrapException.class, () -> builder.build(entries));
    }


    @Test
    void globInTheFirstSegmentExpandsAgainstTheBaseDir() throws URISyntaxException
    {
        // The glob starts at segment 0, so there is no concrete prefix to anchor on and the search
        // root is the config's own directory. Treating such an entry as a plain path instead would
        // "not find" it and skip it with a warning.
        URL[] urls = builder(ErrorMode.WARN).build(List.of("*.jar"));
        assertEquals(List.of("top.jar"), fileNames(urls));
        assertEquals(List.of(), warnings);
    }


    @Test
    void questionMarkInTheFirstSegmentIsAlsoAGlob() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("?op.jar"));
        assertEquals(List.of("top.jar"), fileNames(urls));
        assertEquals(List.of(), warnings);
    }


    @Test
    void globNeverAddsTheSearchRootItself() throws URISyntaxException, IOException
    {
        // 'glob:**' matches the empty relative path, so without the explicit filter the walked
        // directory would put itself on the classpath ahead of its own contents.
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/**"));
        assertEquals(List.of("a.jar", "b.jar", "notes.txt", "sub", "sub/c.jar"),
                relativeNames(urls, tmp.resolve("lib")));
    }


    @Test
    void brokenSymlinkMatchedByAGlobIsStillResolved() throws URISyntaxException, IOException
    {
        // Files.walk reports a dangling symlink, but toRealPath cannot resolve it. The fallback
        // must still yield a usable absolute path — returning nothing there would drop the entry.
        Files.createSymbolicLink(tmp.resolve("lib/dangling.jar"), tmp.resolve("lib/gone.jar"));

        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/*.jar"));

        assertEquals(List.of("a.jar", "b.jar", "dangling.jar"), fileNames(urls));
    }


    @Test
    void directoryEntryYieldsAUrlWithATrailingSlash() throws URISyntaxException
    {
        // URLClassLoader treats a URL without a trailing '/' as a jar file. A directory entry that
        // lost its slash would load nothing at all, silently, and every class in it would be
        // "not found" later with no clue why.
        URL[] urls = builder(ErrorMode.WARN).build(List.of("classes"));
        assertEquals(1, urls.length);
        assertTrue(urls[0].toString().endsWith("/"), urls[0]::toString);
    }


    @Test
    void jarEntryYieldsAUrlWithoutATrailingSlash() throws URISyntaxException
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("lib/a.jar"));
        assertEquals(1, urls.length);
        assertTrue(urls[0].toString().endsWith("a.jar"), urls[0]::toString);
    }


    @Test
    void aMalformedGlobFailsAsABootstrapErrorRatherThanAStackTrace()
    {
        // An unbalanced '[' makes java.nio's glob compiler throw the unchecked
        // PatternSyntaxException. Left uncaught it escapes Bootstrap.main as a stack trace and
        // exit 1, breaking the contract that a launcher-side failure is 'bootstrap: ...' + exit 2.
        BootstrapException e = assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.WARN).build(List.of("lib/[a*.jar")));
        assertTrue(e.getMessage().contains("malformed classpath glob"), e.getMessage());
        assertEquals(PatternSyntaxException.class, e.getCause().getClass());
    }


    @Test
    void anUnparseablePlainEntryFailsAsABootstrapError()
    {
        // A NUL byte is the one character the UNIX path parser rejects; on Windows the reserved
        // set is far wider (see WindowsClasspathGlobTest). Either way it must not escape unchecked.
        BootstrapException e = assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.WARN).build(List.of("lib/a" + (char) 0 + "b.jar")));
        assertTrue(e.getMessage().contains("invalid classpath entry"), e.getMessage());
        assertEquals(InvalidPathException.class, e.getCause().getClass());
    }


    @Test
    void anUnparseableConcretePrefixOfAGlobFailsAsABootstrapError()
    {
        BootstrapException e = assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.WARN).build(List.of("li" + (char) 0 + "b/*.jar")));
        assertTrue(e.getMessage().contains("invalid classpath entry"), e.getMessage());
    }


    @Test
    void entryThatInterpolatedToNothingIsNotTheConfigDirectory()
    {
        // '${env:EXTRA_LIBS}' with the variable exported but empty resolves to "". Path.resolve("")
        // is the base path itself, so treating it as a path would silently put the CONFIG
        // DIRECTORY on the classpath in place of the jars the operator meant to add — and nothing,
        // not even ERROR mode, would say a word, because nothing is missing.
        URL[] urls = builder(ErrorMode.WARN).build(List.of(""));

        assertEquals(0, urls.length);
        assertEquals(1, warnings.size(), () -> warnings.toString());
        assertTrue(warnings.get(0).contains("empty path"), () -> warnings.toString());
    }


    @Test
    void whitespaceOnlyEntryIsTreatedTheSameWay()
    {
        URL[] urls = builder(ErrorMode.WARN).build(List.of("   "));

        assertEquals(0, urls.length);
        assertEquals(1, warnings.size(), () -> warnings.toString());
    }


    @Test
    void entryThatInterpolatedToNothingThrowsUnderError()
    {
        ClasspathBuilder builder = builder(ErrorMode.ERROR);
        List<String> entries = List.of("");
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> builder.build(entries));
        assertTrue(ex.getMessage().contains("empty path"), ex::getMessage);
    }
}
