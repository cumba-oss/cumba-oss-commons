package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigParserTest
{

    private static final Path SRC = Path.of("test.conf");

    private static BootstrapConfig parse(String... lines)
    {
        return ConfigParser.parse(List.of(lines), SRC);
    }


    @Test
    void parsesAllSections()
    {
        BootstrapConfig cfg = parse("# a comment", "; another comment", "", "[bootstrap]",
                "main-class = com.example.Main", "classloader = child-first", "[properties]",
                "a = 1", "b = ${a}/two", "[classpath]", "${a}/lib/*.jar", "/opt/app/classes");

        assertEquals("com.example.Main", cfg.mainClass().orElseThrow());
        assertEquals(ClassLoaderMode.CHILD_FIRST, cfg.classLoaderMode());
        assertEquals(2, cfg.properties().size());
        assertEquals("a", cfg.properties().get(0).getKey());
        assertEquals("1", cfg.properties().get(0).getValue());
        assertEquals("b", cfg.properties().get(1).getKey());
        assertEquals("${a}/two", cfg.properties().get(1).getValue());
        assertEquals(List.of("${a}/lib/*.jar", "/opt/app/classes"), cfg.classpathEntries());
        assertEquals(SRC, cfg.source());
    }


    @Test
    void defaultsWhenBootstrapSectionAbsent()
    {
        BootstrapConfig cfg = parse("[classpath]", "x.jar");
        assertTrue(cfg.mainClass().isEmpty());
        assertEquals(ClassLoaderMode.PARENT_FIRST, cfg.classLoaderMode());
    }


    @Test
    void sectionHeadersAreCaseInsensitiveAndTrimmed()
    {
        BootstrapConfig cfg = parse("[ Properties ]", "k = v");
        assertEquals("v", cfg.properties().get(0).getValue());
    }


    @Test
    void valueMayContainEqualsSign()
    {
        BootstrapConfig cfg = parse("[properties]", "url = a=b=c");
        assertEquals("a=b=c", cfg.properties().get(0).getValue());
    }


    @Test
    void classpathEntriesAreVerbatimLinesNotKeyValue()
    {
        BootstrapConfig cfg = parse("[classpath]", "name=with=equals/*.jar");
        assertEquals(List.of("name=with=equals/*.jar"), cfg.classpathEntries());
    }


    @Test
    void emptyValueIsAllowed()
    {
        BootstrapConfig cfg = parse("[properties]", "k =");
        assertEquals("", cfg.properties().get(0).getValue());
    }


    @Test
    void configIsImmutable()
    {
        BootstrapConfig cfg = parse("[classpath]", "a.jar");
        assertThrows(UnsupportedOperationException.class,
                () -> cfg.classpathEntries().add("b.jar"));
    }


    @Test
    void malformedSectionHeaderFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> parse("[properties"));
        assertTrue(ex.getMessage().contains("malformed section header"));
        assertTrue(ex.getMessage().contains("test.conf:1"));
    }


    @Test
    void entryBeforeAnySectionFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> parse("k = v"));
        assertTrue(ex.getMessage().contains("entry before any [section]"));
    }


    @Test
    void unknownSectionFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bogus]", "k = v"));
        assertTrue(ex.getMessage().contains("unknown section [bogus]"));
    }


    @Test
    void missingEqualsInPropertyFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[properties]", "novalue"));
        assertTrue(ex.getMessage().contains("expected 'key = value'"));
    }


    @Test
    void emptyKeyFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[properties]", "= v"));
        assertTrue(ex.getMessage().contains("empty key"));
    }


    @Test
    void unknownBootstrapKeyFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "wat = x"));
        assertTrue(ex.getMessage().contains("unknown [bootstrap] key"));
    }


    @Test
    void unknownClassloaderTokenFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "classloader = sideways"));
        assertTrue(ex.getMessage().contains("unknown classloader mode"));
    }


    @Test
    void parentFirstTokenParses()
    {
        BootstrapConfig cfg = parse("[bootstrap]", "classloader = PARENT-FIRST");
        assertEquals(ClassLoaderMode.PARENT_FIRST, cfg.classLoaderMode());
    }


    @Test
    void aByteOrderMarkBeforeACommentOnLineOneIsIgnored()
    {
        // "UTF-8 with BOM": Files.readAllLines keeps U+FEFF at the start of the first line.
        BootstrapConfig cfg = parse("\uFEFF# written by Notepad", "[bootstrap]",
                "main-class = a.B");
        assertEquals("a.B", cfg.mainClass().orElseThrow());
    }


    @Test
    void aByteOrderMarkBeforeASectionHeaderIsIgnored()
    {
        BootstrapConfig cfg = parse("\uFEFF[bootstrap]", "main-class = a.B");
        assertEquals("a.B", cfg.mainClass().orElseThrow());
    }


    @Test
    void onlyALeadingByteOrderMarkOnLineOneIsRemoved()
    {
        assertEquals("[x]", ConfigParser.withoutByteOrderMark("\uFEFF[x]"));
        assertEquals("", ConfigParser.withoutByteOrderMark(""));
        assertEquals("a\uFEFF", ConfigParser.withoutByteOrderMark("a\uFEFF"));
        // Past line 1 a U+FEFF is not a BOM and is left for the parser to reject.
        assertThrows(BootstrapException.class, () -> parse("[bootstrap]", "\uFEFFmain-class"));
    }


    @Test
    void userConfigIsRepeatableAndKeepsItsOrder()
    {
        BootstrapConfig cfg = parse("[bootstrap]", "user-config = ${env:A}/a.conf",
                "user-config = b.conf", "main-class = x.Y");
        assertEquals(List.of("${env:A}/a.conf", "b.conf"), cfg.userConfigCandidates());
    }


    @Test
    void noUserConfigMeansNoCandidatesAndNoReservedKeys()
    {
        BootstrapConfig cfg = parse("[bootstrap]", "main-class = x.Y");
        assertEquals(List.of(), cfg.userConfigCandidates());
        assertEquals(Set.of(), cfg.reservedKeys());
    }


    @Test
    void emptyUserConfigFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "user-config ="));
        assertEquals("test.conf:2: empty value for [bootstrap] key 'user-config'", ex.getMessage());
    }


    @Test
    void reservedKeysAreCommaSeparatedMergedAndStripped()
    {
        BootstrapConfig cfg = parse("[bootstrap]", "reserved = a.b ,c.d,, ", "reserved = e");
        assertEquals(Set.of("a.b", "c.d", "e"), cfg.reservedKeys());
    }


    @Test
    void emptyReservedFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "reserved ="));
        assertEquals("test.conf:2: empty value for [bootstrap] key 'reserved'", ex.getMessage());
    }


    @Test
    void reservedOfOnlyCommasFails()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "reserved = , ,"));
        assertEquals("test.conf:2: empty value for [bootstrap] key 'reserved'", ex.getMessage());
    }


    @Test
    void reservedKeysMissingACommaFail()
    {
        // 'a.b c.d' as one key would match nothing: both would silently stay unreserved.
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "reserved = a.b c.d, e"));
        assertEquals("test.conf:2: [bootstrap] reserved takes comma-separated keys, not 'a.b c.d'",
                ex.getMessage());
    }


    @Test
    void overlayParsesProperties()
    {
        List<Map.Entry<String, String>> props = ConfigParser.parseOverlay(
                List.of("\uFEFF# user", "", "[Properties]", "a = 1", "; c", "b = ${a}"), SRC);
        assertEquals(List.of(Map.entry("a", "1"), Map.entry("b", "${a}")), props);
    }


    @Test
    void emptyOverlayHasNoProperties()
    {
        assertEquals(List.of(), ConfigParser.parseOverlay(List.of("# nothing yet"), SRC));
    }


    @Test
    void overlayRejectsTheClasspathSection()
    {
        BootstrapException ex = assertThrows(BootstrapException.class, () -> ConfigParser
                .parseOverlay(List.of("[properties]", "a = 1", "[classpath]", "/evil/*.jar"), SRC));
        assertEquals("test.conf:3: a user configuration may hold only [properties], not"
                + " [classpath]", ex.getMessage());
    }


    @Test
    void overlayRejectsTheBootstrapSection()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigParser.parseOverlay(List.of("[bootstrap]", "main-class = x.Y"), SRC));
        assertTrue(ex.getMessage().startsWith("test.conf:1: "), ex::getMessage);
    }


    @Test
    void overlayRejectsAnEntryBeforeTheHeader()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigParser.parseOverlay(List.of("a = 1"), SRC));
        assertEquals("test.conf:1: entry before the [properties] header: a = 1", ex.getMessage());
    }


    @Test
    void overlayRejectsAMalformedEntry()
    {
        assertThrows(BootstrapException.class,
                () -> ConfigParser.parseOverlay(List.of("[properties]", "no equals"), SRC));
    }


    @Test
    void singleInstanceIsTheGatesOptIn()
    {
        assertEquals(Optional.empty(), parse("[bootstrap]", "main-class = x.Y").singleInstanceDir(),
                "absent: no gate");
        assertEquals(Optional.of("${sys:user.home}/.cumbaDataBrowser"),
                parse("[bootstrap]", "single-instance = ${sys:user.home}/.cumbaDataBrowser")
                        .singleInstanceDir());
        assertEquals(Optional.of("b"),
                parse("[bootstrap]", "single-instance = a", "single-instance = b")
                        .singleInstanceDir(),
                "a repeated key overwrites");
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> parse("[bootstrap]", "single-instance ="));
        assertEquals("test.conf:2: empty value for [bootstrap] key 'single-instance'",
                ex.getMessage());
    }
}
