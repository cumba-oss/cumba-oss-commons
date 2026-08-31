package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
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
}
