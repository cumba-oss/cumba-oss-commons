package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executes {@link ClasspathBuilder} against an in-memory <em>Windows</em> filesystem.
 *
 * <p>
 * {@code win_x64} is a shipped platform, and every one of these cases failed there before the
 * string-splitting fix: {@code Path.of} rejects {@code *} and {@code ?} outright on Windows
 * (reserved set {@code <>:"|?*}), so the README's own canonical {@code ${app.home}/lib/*.jar} died
 * with an {@link InvalidPathException} stack trace and exit 1 rather than launching.
 *
 * <p>
 * jimfs is used as the oracle because it reproduces both Windows rules this class depends on: the
 * reserved-character rejection, and the glob compiler's treatment of {@code '/'} as the separator
 * and {@code '\'} as the <em>escape</em> character. Both were cross-checked against the JDK 25
 * sources of {@code sun.nio.fs.WindowsPathParser} and {@code sun.nio.fs.Globs}.
 */
class WindowsClasspathGlobTest
{

    private FileSystem windows;

    private Path appHome;

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void layout() throws IOException
    {
        windows = Jimfs.newFileSystem(Configuration.windows());
        appHome = windows.getPath("C:\\app");
        Files.createDirectories(appHome.resolve("lib\\sub"));
        Files.createFile(appHome.resolve("lib\\a.jar"));
        Files.createFile(appHome.resolve("lib\\b.jar"));
        Files.createFile(appHome.resolve("lib\\sub\\c.jar"));
        Files.createFile(appHome.resolve("lib\\notes.txt"));
        Files.createFile(appHome.resolve("top.jar"));
    }


    @AfterEach
    void closeFileSystem() throws IOException
    {
        windows.close();
    }


    private ClasspathBuilder builder(ErrorMode mode)
    {
        return new ClasspathBuilder(appHome, mode, warnings::add);
    }


    private static List<String> fileNames(URL[] urls)
    {
        List<String> names = new ArrayList<>();
        for (URL url : urls)
        {
            String path = url.getPath();
            names.add(path.substring(path.lastIndexOf('/') + 1));
        }
        return names;
    }


    @Test
    void theReadmesCanonicalEntryExpandsInsteadOfThrowing()
    {
        // '${app.home}/lib/*.jar' with app.home = 'C:\app'. Before the fix Path.of(entry) threw
        // InvalidPathException here, on the one shape the README tells every operator to write.
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("C:\\app/lib/*.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void anAllBackslashAbsoluteGlobExpands()
    {
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("C:\\app\\lib\\*.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void aRelativeBackslashGlobResolvesAgainstTheConfigDirectory()
    {
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("lib\\*.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void aGlobInTheFirstSegmentExpandsAgainstTheConfigDirectory()
    {
        // No concrete prefix at all, so there is nothing to anchor on and the search root is the
        // config directory itself.
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("*.jar"));
        assertEquals(List.of("top.jar"), fileNames(urls));
    }


    @Test
    void questionMarkIsAlsoRejectedByTheWindowsParserAndStillWorks()
    {
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("lib\\?.jar"));
        assertEquals(List.of("a.jar", "b.jar"), fileNames(urls));
    }


    @Test
    void doubleStarCrossesDirectoriesOnWindowsToo()
    {
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("lib\\**.jar"));
        assertEquals(List.of("a.jar", "b.jar", "c.jar"), fileNames(urls));
    }


    @Test
    void aSeparatorInsideThePatternSeparatesRatherThanEscapes()
    {
        // ⭐ The load-bearing case for the second half of the fix. The pattern here spans two
        // segments, so it is the only shape in which the separator translation is observable: the
        // matcher must be built as 'glob:**/*.jar'. Written with the platform separator instead
        // ('glob:**\*.jar') the '\' is the glob ESCAPE character, '*' becomes a literal asterisk,
        // and this matches NOTHING — silently, with the launcher simply starting without its jars.
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("lib\\**\\*.jar"));
        assertEquals(List.of("c.jar"), fileNames(urls));
    }


    @Test
    void forwardAndBackwardSeparatorsAreInterchangeableInOneEntry()
    {
        URL[] urls = builder(ErrorMode.ERROR).build(List.of("lib/**\\*.jar"));
        assertEquals(List.of("c.jar"), fileNames(urls));
    }


    @Test
    void aGlobMatchingNothingIsStillRoutedThroughTheErrorMode()
    {
        assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.ERROR).build(List.of("lib\\*.zip")));
    }


    @Test
    void anEntryThatInterpolatedToNothingIsNotTheConfigDirectory()
    {
        // The wave-0 fix for '${env:EXTRA_LIBS}' exported-but-empty, re-asserted on Windows:
        // Path.resolve("") returns the base path, which would put C:\app itself on the classpath.
        URL[] urls = builder(ErrorMode.WARN).build(List.of(""));
        assertEquals(0, urls.length);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("empty path"));
    }


    @Test
    void aReservedCharacterInAPlainEntryFailsAsABootstrapError()
    {
        // '|' is reserved on Windows exactly as '*' is. Without the catch this escapes
        // Bootstrap.main unchecked: a stack trace and exit 1 instead of 'bootstrap: ...' and 2.
        BootstrapException e = assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.WARN).build(List.of("lib\\a|b.jar")));
        assertTrue(e.getMessage().contains("invalid classpath entry"), e.getMessage());
        assertEquals(InvalidPathException.class, e.getCause().getClass());
    }


    @Test
    void aReservedCharacterBeforeTheGlobFailsAsABootstrapError()
    {
        BootstrapException e = assertThrows(BootstrapException.class,
                () -> builder(ErrorMode.WARN).build(List.of("li|b\\*.jar")));
        assertTrue(e.getMessage().contains("invalid classpath entry"), e.getMessage());
    }
}
