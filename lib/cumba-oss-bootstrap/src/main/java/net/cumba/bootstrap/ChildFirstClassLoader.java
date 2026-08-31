package net.cumba.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A {@link URLClassLoader} with child-first ("parent-last") delegation: classes <em>and</em>
 * resources are resolved from the configured URLs before the parent is consulted. Platform/JDK
 * namespaces ({@code java.*}, {@code javax.*}, {@code jdk.*}, {@code sun.*}) are always delegated
 * to the parent first so the application can never shadow core classes. Resources are also
 * child-first so a shadowed class and its co-located resources (and {@code META-INF/services} SPI
 * files) resolve from the same jar.
 */
final class ChildFirstClassLoader extends URLClassLoader
{

    ChildFirstClassLoader(URL[] urls, ClassLoader parent)
    {
        super(urls, parent);
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
        synchronized (getClassLoadingLock(name))
        {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null)
            {
                loaded = isPlatformClass(name) ? super.loadClass(name, false)
                        : loadLocalFirst(name);
            }
            if (resolve)
            {
                resolveClass(loaded);
            }
            return loaded;
        }
    }


    private Class<?> loadLocalFirst(String name) throws ClassNotFoundException
    {
        try
        {
            return findClass(name);
        }
        catch (ClassNotFoundException notLocal)
        {
            return super.loadClass(name, false);
        }
    }


    @Override
    public @Nullable URL getResource(String name)
    {
        URL local = findResource(name);
        if (local != null)
        {
            return local;
        }
        ClassLoader parent = getParent();
        return parent != null ? parent.getResource(name) : null;
    }


    @Override
    public Enumeration<URL> getResources(String name) throws IOException
    {
        // Child-first ordering: local URLs first, then the parent's, de-duplicated. De-dup keys on
        // the URL string rather than the URL itself, since URL.equals/hashCode can block on DNS.
        List<URL> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(findResources(name), ordered, seen);
        ClassLoader parent = getParent();
        if (parent != null)
        {
            collect(parent.getResources(name), ordered, seen);
        }
        return Collections.enumeration(ordered);
    }


    private static void collect(Enumeration<URL> source, List<URL> ordered, Set<String> seen)
    {
        while (source.hasMoreElements())
        {
            URL url = source.nextElement();
            if (seen.add(url.toString()))
            {
                ordered.add(url);
            }
        }
    }


    private static boolean isPlatformClass(String name)
    {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.");
    }
}
