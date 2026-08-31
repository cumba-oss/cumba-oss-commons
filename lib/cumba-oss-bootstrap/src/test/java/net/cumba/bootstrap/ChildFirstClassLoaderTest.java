package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChildFirstClassLoaderTest
{

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
    void getResourcesIncludesLocalResource() throws IOException
    {
        List<URL> found = new ArrayList<>();
        for (Enumeration<URL> e = loader.getResources("data.txt"); e.hasMoreElements();)
        {
            found.add(e.nextElement());
        }
        assertEquals(1, found.size());
        assertTrue(found.get(0).toString().endsWith("data.txt"), () -> found.toString());
    }


    @Test
    void platformClassesStillResolveThroughParent() throws ClassNotFoundException
    {
        assertEquals(String.class, loader.loadClass("java.lang.String"));
    }
}
