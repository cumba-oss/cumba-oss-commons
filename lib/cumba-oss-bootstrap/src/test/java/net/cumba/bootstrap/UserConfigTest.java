package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserConfigTest
{

    @TempDir
    Path tmp;

    private final Map<String, String> env = new HashMap<>();

    private final Map<String, String> sys = new HashMap<>();

    private Path installDir;

    private Path home;

    @BeforeEach
    void setUp() throws IOException
    {
        installDir = Files.createDirectories(tmp.resolve("install"));
        home = Files.createDirectories(tmp.resolve("home"));
        sys.put("user.home", home.toString());
    }


    private BootstrapConfig config(String... candidates)
    {
        return new BootstrapConfig(installDir.resolve("app.conf"), Optional.empty(),
                ClassLoaderMode.PARENT_FIRST, List.of(), List.of(), List.of(candidates),
                Set.of("app.dir"));
    }


    private Optional<UserConfig.Location> locate(BootstrapConfig config)
    {
        return UserConfig.locate(config, installDir, env::get, sys::get);
    }


    private Path userFile(String relative) throws IOException
    {
        Path file = home.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "[properties]\n");
    }


    @Test
    void noDeclaredCandidateMeansNoOverlay()
    {
        assertEquals(Optional.empty(), locate(config()));
    }


    @Test
    void theOverrideIsIgnoredWithoutADeclaredCandidate() throws IOException
    {
        // L2: a JAVA_TOOL_OPTIONS exported for the data browser must not overlay the CLI/REST.
        sys.put(UserConfig.OVERRIDE_PROPERTY, userFile("x.conf").toString());
        assertEquals(Optional.empty(), locate(config()));
    }


    @Test
    void noExistingCandidateFindsNothingButListsWhereItLooked()
    {
        UserConfig.Location location = locate(
                config("${sys:user.home}/.cumbaDataBrowser/databrowser.conf")).orElseThrow();
        assertEquals(Optional.empty(), location.applied());
        assertEquals(List.of(home.resolve(".cumbaDataBrowser/databrowser.conf")),
                location.candidates());
    }


    @Test
    void theFirstExistingCandidateWins() throws IOException
    {
        Path second = userFile("b/app.conf");
        Path third = userFile("c/app.conf");
        UserConfig.Location location = locate(config("${sys:user.home}/a/app.conf",
                "${sys:user.home}/b/app.conf", "${sys:user.home}/c/app.conf")).orElseThrow();
        assertEquals(Optional.of(second), location.applied());
        assertEquals(3, location.candidates().size());
        assertEquals(third, location.candidates().get(2));
    }


    @Test
    void aDirectoryIsNotACandidateFile() throws IOException
    {
        Files.createDirectories(home.resolve("a/app.conf"));
        Path second = userFile("b/app.conf");
        UserConfig.Location location = locate(
                config("${sys:user.home}/a/app.conf", "${sys:user.home}/b/app.conf")).orElseThrow();
        assertEquals(Optional.of(second), location.applied());
    }


    @Test
    void anUnresolvableCandidateIsSkippedAndNotListed() throws IOException
    {
        Path file = userFile("app.conf");
        UserConfig.Location location = locate(
                config("${env:APPDATA}/app.conf", "${sys:user.home}/app.conf")).orElseThrow();
        assertEquals(List.of(file), location.candidates());
        assertEquals(Optional.of(file), location.applied());
    }


    @Test
    void aCandidateResolvingToNothingIsSkippedNotTheInstallation() throws IOException
    {
        // Path.of("") would resolve to bootstrap.dir and list the installation as a candidate.
        Path file = userFile("app.conf");
        UserConfig.Location location = locate(
                config("${env:NOT_SET:-}", "${sys:user.home}/app.conf")).orElseThrow();
        assertEquals(List.of(file), location.candidates());
    }


    @Test
    void everyCandidateSkippedPublishesAnEmptyList()
    {
        UserConfig.Location location = locate(config("${env:APPDATA}/app.conf")).orElseThrow();
        assertEquals(List.of(), location.candidates());
        assertEquals(Optional.empty(), location.applied());
    }


    @Test
    void anOverrideNamingADirectoryIsReportedAsSuch() throws IOException
    {
        Path dir = Files.createDirectories(home.resolve("a-dir"));
        sys.put(UserConfig.OVERRIDE_PROPERTY, dir.toString());
        BootstrapConfig cfg = config("app.conf");
        BootstrapException ex = assertThrows(BootstrapException.class, () -> locate(cfg));
        assertEquals("user config is not a regular file: " + dir + " (set by -D"
                + UserConfig.OVERRIDE_PROPERTY + ")", ex.getMessage());
    }


    @Test
    void aCandidateSyntaxErrorFails()
    {
        BootstrapConfig cfg = config("${sys:user.home/app.conf");
        assertThrows(BootstrapException.class, () -> locate(cfg));
    }


    @Test
    void candidatesDoNotSeeBundledPropertiesButDoSeeBootstrapDir() throws IOException
    {
        Path portable = Files.writeString(installDir.resolve("user.conf"), "[properties]\n");
        BootstrapConfig cfg = new BootstrapConfig(installDir.resolve("app.conf"), Optional.empty(),
                ClassLoaderMode.PARENT_FIRST, List.of(Map.entry("where", "/x")), List.of(),
                List.of("${where}/app.conf", "${bootstrap.dir}/user.conf"), Set.of());
        UserConfig.Location location = locate(cfg).orElseThrow();
        assertEquals(List.of(portable), location.candidates());
    }


    @Test
    void aRelativeCandidateResolvesAgainstTheInstallation() throws IOException
    {
        Path file = Files.writeString(installDir.resolve("local.conf"), "[properties]\n");
        assertEquals(Optional.of(file), locate(config("local.conf")).orElseThrow().applied());
    }


    @Test
    void anInvalidCandidatePathFails()
    {
        BootstrapConfig cfg = config("${sys:bad}");
        sys.put("bad", "a\0b");
        BootstrapException ex = assertThrows(BootstrapException.class, () -> locate(cfg));
        assertInstanceOf(InvalidPathException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("user-config is not a valid path"), ex::getMessage);
    }


    @Test
    void theOverrideReplacesTheCandidates() throws IOException
    {
        Path explicit = userFile("explicit.conf");
        userFile("app.conf");
        sys.put(UserConfig.OVERRIDE_PROPERTY, " " + explicit + " ");
        UserConfig.Location location = locate(config("${sys:user.home}/app.conf")).orElseThrow();
        assertEquals(List.of(explicit), location.candidates());
        assertEquals(Optional.of(explicit), location.applied());
    }


    @Test
    void aRelativeOverrideResolvesAgainstTheWorkingDirectory()
    {
        sys.put(UserConfig.OVERRIDE_PROPERTY, "no-such-user.conf");
        BootstrapConfig cfg = config("app.conf");
        BootstrapException ex = assertThrows(BootstrapException.class, () -> locate(cfg));
        assertEquals("user config not found: "
                + Path.of("no-such-user.conf").toAbsolutePath().normalize() + " (set by -D"
                + UserConfig.OVERRIDE_PROPERTY + ")", ex.getMessage());
    }


    @Test
    void aBlankOverrideCountsAsUnset() throws IOException
    {
        Path file = userFile("app.conf");
        sys.put(UserConfig.OVERRIDE_PROPERTY, "  ");
        assertEquals(Optional.of(file),
                locate(config("${sys:user.home}/app.conf")).orElseThrow().applied());
    }


    @Test
    void anInvalidOverrideFails()
    {
        sys.put(UserConfig.OVERRIDE_PROPERTY, "a\0b");
        BootstrapConfig cfg = config("app.conf");
        BootstrapException ex = assertThrows(BootstrapException.class, () -> locate(cfg));
        assertTrue(
                ex.getMessage()
                        .startsWith("-D" + UserConfig.OVERRIDE_PROPERTY + " is not a valid path"),
                ex::getMessage);
    }


    @Test
    void joinedCandidatesUseThePathSeparator()
    {
        UserConfig.Location location = new UserConfig.Location(
                List.of(Path.of("/a"), Path.of("/b")), Optional.empty());
        assertEquals(Path.of("/a") + File.pathSeparator + Path.of("/b"),
                location.joinedCandidates());
        assertEquals("", new UserConfig.Location(List.of(), Optional.empty()).joinedCandidates());
    }


    @Test
    void reservedKeysAreTheBuiltInOnesAndTheConfigsOwn()
    {
        BootstrapConfig cfg = config();
        for (String key : List.of("bootstrap.dir", "bootstrap.config", "bootstrap.user.config",
                "bootstrap.user.config.applied", "bootstrap.user.config.candidates",
                "bootstrap.error.dialog", "classpath.error.mode", "app.dir"))
        {
            assertTrue(UserConfig.isReserved(cfg, key), key);
        }
        assertFalse(UserConfig.isReserved(cfg, "openai.api.model"));
    }
}
