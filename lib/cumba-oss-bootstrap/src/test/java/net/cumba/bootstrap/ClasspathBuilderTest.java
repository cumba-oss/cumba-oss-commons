package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
}
