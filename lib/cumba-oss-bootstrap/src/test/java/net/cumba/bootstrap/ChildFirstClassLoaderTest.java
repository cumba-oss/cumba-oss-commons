package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildFirstClassLoaderTest
{

    /**
     * A class the platform classloader can see that is <em>not</em> in a reserved namespace, so
     * child-first delegation is allowed to shadow it. {@code org.w3c.dom} ships in the JDK's
     * {@code java.xml} module, which makes it the one thing a test can put on both sides of the
     * delegation fence without a compiler: the bytes are readable as a resource and the same bytes
     * can be re-defined by a {@link java.net.URLClassLoader}.
     */
    private static final String SHADOWABLE_CLASS = "org.w3c.dom.Node";

    /** Visible to the parent, never copied into the child — exercises the parent fallback. */
    private static final String PARENT_ONLY_CLASS = "org.xml.sax.InputSource";

    @TempDir
    Path tmp;

    private ChildFirstClassLoader loader;

    @BeforeEach
    void setUp() throws IOException
    {
        Files.writeString(tmp.resolve("data.txt"), "local");
        URL[] urls =
        {
                tmp.toUri().toURL()
        };
        loader = new ChildFirstClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }


    /**
     * Copies a class the parent can already see into the child's directory, so the same binary name
     * is resolvable on both sides of the delegation fence.
     */
    private void shadow(String binaryName) throws IOException
    {
        String resource = binaryName.replace('.', '/') + ".class";
        Path target = tmp.resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream in = ClassLoader.getPlatformClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(in, () -> "the JDK no longer exposes " + resource);
            Files.write(target, in.readAllBytes());
        }
    }


    private static List<URL> list(Enumeration<URL> e)
    {
        List<URL> found = new ArrayList<>();
        while (e.hasMoreElements())
        {
            found.add(e.nextElement());
        }
        return found;
    }


    @Test
    void getResourceFindsLocalResourceFirst()
    {
        URL resource = loader.getResource("data.txt");
        assertNotNull(resource);
        assertTrue(resource.toString().endsWith("data.txt"), resource::toString);
    }


    @Test
    void getResourceReturnsNullWhenAbsentEverywhere()
    {
        assertNull(loader.getResource("does/not/exist.txt"));
    }


    @Test
    void getResourceFallsBackToTheParent()
    {
        // Not on the child's classpath, so the only way to find it is to delegate. If the parent
        // branch is dropped the loader silently sees an empty world instead of the JDK's.
        URL fromParent = loader.getResource("java/lang/Object.class");
        assertNotNull(fromParent, "a JDK resource must still be reachable through the parent");
    }


    @Test
    void getResourcesIncludesLocalResource() throws IOException
    {
        List<URL> found = list(loader.getResources("data.txt"));
        assertEquals(1, found.size());
        assertTrue(found.get(0).toString().endsWith("data.txt"), () -> found.toString());
    }


    @Test
    void getResourcesIncludesParentResources() throws IOException
    {
        // Nothing local matches, so every element must have come from the parent. Dropping the
        // parent pass would return an empty enumeration — a shadowed SPI lookup finding nothing.
        List<URL> found = list(loader.getResources("java/lang/Object.class"));
        assertEquals(1, found.size(), () -> found.toString());
    }


    @Test
    void getResourcesListsLocalBeforeParent() throws IOException, URISyntaxException
    {
        shadow(SHADOWABLE_CLASS);
        String resource = SHADOWABLE_CLASS.replace('.', '/') + ".class";

        List<URL> found = list(loader.getResources(resource));

        assertEquals(2, found.size(), () -> found.toString());
        assertEquals(tmp.resolve(resource), Path.of(found.get(0).toURI()),
                () -> "the child's copy must be listed first: " + found);
        assertTrue(found.get(1).toString().startsWith("jrt:"), () -> found.toString());
    }


    @Test
    void childFirstWinsForAClassTheParentCanAlsoSee() throws Exception
    {
        shadow(SHADOWABLE_CLASS);

        Class<?> loaded = loader.loadClass(SHADOWABLE_CLASS);

        // The whole point of the mode: the configured classpath is consulted first. If delegation
        // silently degraded to parent-first this would be the JDK's copy and nothing else would
        // tell us.
        assertSame(loader, loaded.getClassLoader(),
                "child-first must define the shadowed class itself");
    }


    @Test
    void resolveFlagStillYieldsTheChildDefinedClass() throws Exception
    {
        shadow(SHADOWABLE_CLASS);

        Class<?> loaded = loader.loadClass(SHADOWABLE_CLASS, true);

        assertSame(loader, loaded.getClassLoader());
    }


    @Test
    void classOnlyOnTheParentFallsBackToTheParent() throws Exception
    {
        Class<?> loaded = loader.loadClass(PARENT_ONLY_CLASS);

        assertNotNull(loaded);
        assertNotSame(loader, loaded.getClassLoader(),
                "a class absent from the configured classpath must come from the parent");
    }


    @Test
    void unknownClassStillFails()
    {
        org.junit.jupiter.api.Assertions.assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("net.cumba.definitely.Absent"));
    }


    /**
     * The reserved namespaces must be delegated even when the configured classpath carries a copy —
     * otherwise an application jar could shadow a core class and the JVM would silently run against
     * two different versions of it. Each name here is checked with its own copy planted in the
     * child, so each prefix in {@code isPlatformClass} is load-bearing on its own.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "java.sql.Time", "javax.naming.Name", "jdk.net.ExtendedSocketOptions",
            "sun.net.www.ParseUtil"
    })
    void reservedNamespacesAreAlwaysDelegatedEvenWhenShadowed(String binaryName) throws Exception
    {
        shadow(binaryName);

        Class<?> loaded = loader.loadClass(binaryName);

        assertNotSame(loader, loaded.getClassLoader(),
                () -> binaryName + " must be resolved through the parent, not the child copy");
    }


    @Test
    void platformClassesStillResolveThroughParent() throws ClassNotFoundException
    {
        assertEquals(String.class, loader.loadClass("java.lang.String"));
    }
}
