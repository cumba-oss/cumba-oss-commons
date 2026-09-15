package net.cumba.bootstrap;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jspecify.annotations.Nullable;

/**
 * Applies a parsed {@link BootstrapConfig} and hands control to the target program: seeds
 * {@code bootstrap.dir}, applies system properties, assembles and de-duplicates the classpath,
 * builds an isolated classloader, then reflectively invokes the target {@code main}.
 *
 * <p>
 * {@link #launch} mutates global JVM state (system properties and the thread context classloader)
 * and does not restore it; it is intended for a one-shot launcher process, not for repeated or
 * concurrent invocation inside a hosting JVM.
 */
public final class Launcher
{

    /** System property seeded to the config file's directory before properties are applied. */
    public static final String BOOTSTRAP_DIR_PROPERTY = "bootstrap.dir";

    /** System property selecting the {@link ErrorMode} for classpath expansion. */
    public static final String CLASSPATH_ERROR_MODE_PROPERTY = "classpath.error.mode";

    /** Manifest header consulted for the target main class when the config omits it. */
    public static final String MANIFEST_MAIN_CLASS_ATTRIBUTE = "Bootstrap-Main-Class";

    private Launcher()
    {
    }


    /**
     * Runs the configured target program.
     *
     * @param config
     *            the parsed config
     * @param args
     *            command-line arguments to pass through to the target {@code main}
     * @param protectionDomain
     *            the launcher's protection domain (used for the manifest main-class fallback)
     * @throws Exception
     *             any checked exception thrown by the target {@code main} (propagated as-is); a
     *             {@link BootstrapException} for any launcher-side setup failure
     */
    public static void launch(BootstrapConfig config, String[] args,
            ProtectionDomain protectionDomain)
        throws Exception
    {
        Path baseDir = configDirectory(config.source());
        System.setProperty(BOOTSTRAP_DIR_PROPERTY, baseDir.toString());

        Map<String, String> resolvedProperties = new LinkedHashMap<>();
        resolvedProperties.put(BOOTSTRAP_DIR_PROPERTY, baseDir.toString());
        VariableResolver resolver = new VariableResolver(System::getenv, System::getProperty,
                resolvedProperties);

        applyProperties(config, resolver, resolvedProperties);

        ErrorMode errorMode = ErrorMode
                .fromProperty(System.getProperty(CLASSPATH_ERROR_MODE_PROPERTY));
        URL[] classpath = buildClasspath(config, resolver, baseDir, errorMode);

        ClassLoader loader = createClassLoader(classpath, config.classLoaderMode());
        Thread.currentThread().setContextClassLoader(loader);

        String mainClassName = resolveMainClassName(config, resolver, protectionDomain);
        invokeMain(mainClassName, loader, args);
    }


    private static void applyProperties(BootstrapConfig config, VariableResolver resolver,
            Map<String, String> resolvedProperties)
    {
        for (Map.Entry<String, String> property : config.properties())
        {
            String value = resolver.resolve(property.getValue(), config.source());
            System.setProperty(property.getKey(), value);
            resolvedProperties.put(property.getKey(), value);
        }
    }


    private static URL[] buildClasspath(BootstrapConfig config, VariableResolver resolver,
            Path baseDir, ErrorMode errorMode)
    {
        List<String> resolved = new ArrayList<>(config.classpathEntries().size());
        for (String entry : config.classpathEntries())
        {
            resolved.add(resolver.resolve(entry, config.source()));
        }
        return new ClasspathBuilder(baseDir, errorMode).build(resolved);
    }


    private static ClassLoader createClassLoader(URL[] classpath, ClassLoaderMode mode)
    {
        ClassLoader parent = ClassLoader.getPlatformClassLoader();
        return mode == ClassLoaderMode.CHILD_FIRST ? new ChildFirstClassLoader(classpath, parent)
                : new URLClassLoader(classpath, parent);
    }


    private static String resolveMainClassName(BootstrapConfig config, VariableResolver resolver,
            ProtectionDomain protectionDomain)
    {
        Optional<String> fromConfig = config.mainClass()
                .map(value -> resolver.resolve(value, config.source()).strip())
                .filter(value -> !value.isEmpty());
        if (fromConfig.isPresent())
        {
            return fromConfig.get();
        }

        String fromManifest = manifestMainClass(protectionDomain);
        if (fromManifest != null)
        {
            return fromManifest;
        }

        throw new BootstrapException("no target main class: set '[bootstrap] main-class' in "
                + config.source() + " or a '" + MANIFEST_MAIN_CLASS_ATTRIBUTE
                + "' manifest header in the launcher jar");
    }


    private static void invokeMain(String mainClassName, ClassLoader loader, String[] args)
        throws Exception
    {
        Class<?> mainClass;
        try
        {
            mainClass = Class.forName(mainClassName, true, loader);
        }
        catch (ClassNotFoundException e)
        {
            throw new BootstrapException(
                    "target main class not found on the configured classpath: " + mainClassName, e);
        }

        Method main;
        try
        {
            main = mainClass.getMethod("main", String[].class);
        }
        catch (NoSuchMethodException e)
        {
            throw new BootstrapException(
                    "no 'public static void main(String[])' in " + mainClassName, e);
        }
        if (!Modifier.isStatic(main.getModifiers()))
        {
            throw new BootstrapException("main(String[]) is not static in " + mainClassName);
        }

        try
        {
            main.invoke(null, (Object) args);
        }
        catch (IllegalAccessException e)
        {
            throw new BootstrapException("cannot access main(String[]) in " + mainClassName, e);
        }
        catch (InvocationTargetException e)
        {
            throw launderTargetException(e);
        }
    }


    /**
     * Unwraps the exception thrown by the target {@code main} so it propagates as a genuine target
     * failure rather than a launcher ({@link BootstrapException}) failure. {@link Error}s are
     * rethrown directly; a {@code null} cause (legal but exotic) yields the original
     * {@link InvocationTargetException}.
     *
     * <p>
     * Package-private rather than private so the two exotic branches — a {@code null} cause and a
     * cause that is neither {@link Error} nor {@link Exception} — can be asserted directly.
     * {@link java.lang.reflect.Method#invoke} cannot be made to produce either of them.
     *
     * @param e
     *            the reflection wrapper caught from {@code main.invoke}
     * @return the exception to throw in its place
     */
    static Exception launderTargetException(InvocationTargetException e)
    {
        Throwable cause = e.getCause();
        if (cause == null)
        {
            return e;
        }
        if (cause instanceof Error error)
        {
            throw error;
        }
        if (cause instanceof Exception exception)
        {
            return exception;
        }
        return new BootstrapException("target main threw", cause);
    }


    private static Path configDirectory(Path configSource)
    {
        Path absolute = configSource.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        return parent != null ? parent : absolute;
    }


    /**
     * Reads the {@value #MANIFEST_MAIN_CLASS_ATTRIBUTE} header from the launcher's own jar.
     *
     * <p>
     * Every way of not having one — no code source, a location that is not a filesystem path, an
     * exploded directory, an unreadable jar, no manifest, no header, a blank header — is reported
     * the same way, as {@code null}. The fallback is optional by design, so "cannot read it" means
     * "there isn't one" and the caller reports the missing main class; validating here rather than
     * at the call site keeps that single meaning in one place.
     *
     * @param protectionDomain
     *            the launcher's protection domain
     * @return the declared main class, stripped and guaranteed non-blank, or {@code null}
     */
    private static @Nullable String manifestMainClass(ProtectionDomain protectionDomain)
    {
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null)
        {
            return null;
        }
        Path jar;
        try
        {
            jar = Path.of(codeSource.getLocation().toURI());
        }
        catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e)
        {
            // Same reasoning as ConfigLocator.jarLocation: a code source that is not a filesystem
            // path (a 'jar:' URL from a nested-jar loader, say) makes Path.of throw an unchecked
            // FileSystemNotFoundException. Here the manifest is only a fallback, so 'not readable'
            // simply means 'no header' and the caller reports the missing main class.
            return null;
        }
        if (!Files.isRegularFile(jar))
        {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar.toFile()))
        {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null)
            {
                return null;
            }
            String header = manifest.getMainAttributes().getValue(MANIFEST_MAIN_CLASS_ATTRIBUTE);
            return header == null || header.isBlank() ? null : header.strip();
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
