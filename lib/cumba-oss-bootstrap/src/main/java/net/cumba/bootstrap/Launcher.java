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
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.cumba.bootstrap.instance.InstanceGate;
import org.jspecify.annotations.Nullable;

/**
 * Applies a parsed {@link BootstrapConfig} and hands control to the target program: seeds
 * {@code bootstrap.dir}, applies system properties, assembles and de-duplicates the classpath,
 * resolves the main class, applies the optional user overlay ({@link UserConfig}), builds an
 * isolated classloader, then reflectively invokes the target {@code main}.
 *
 * <p>
 * A config naming {@code [bootstrap] single-instance = <scope>} passes the <em>single-instance
 * gate</em> ({@link InstanceGate}) between the user overlay and the class loader - the class path
 * is fixed but nothing of the application is loaded yet, and the on/off switch
 * ({@value #SINGLE_INSTANCE_PROPERTY}, which the user overlay may set) is in effect. A start whose
 * files were handed off to the running primary returns from {@link #launch} without starting the
 * application. Without the key the gate is not touched and nothing is published.
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

    /**
     * System property naming one explicit user overlay file, replacing the config's
     * {@code [bootstrap] user-config} candidates; see {@link UserConfig}.
     */
    public static final String USER_CONFIG_OVERRIDE_PROPERTY = "bootstrap.user.config";

    /**
     * System property the launcher publishes for the target program: the absolute path of the user
     * overlay it applied, unset when none was. Part of the launcher's public contract - an
     * application that logs it spells the same name, and its build should pin the two equal.
     */
    public static final String USER_CONFIG_APPLIED_PROPERTY = "bootstrap.user.config.applied";

    /**
     * System property the launcher publishes for the target program: where it looked for the user
     * overlay, the resolved candidate paths joined with {@link java.io.File#pathSeparator}. Same
     * contract as {@link #USER_CONFIG_APPLIED_PROPERTY}.
     */
    public static final String USER_CONFIG_CANDIDATES_PROPERTY = "bootstrap.user.config.candidates";

    /** Manifest header consulted for the target main class when the config omits it. */
    public static final String MANIFEST_MAIN_CLASS_ATTRIBUTE = "Bootstrap-Main-Class";

    /**
     * System property switching the single-instance gate off ({@code false}); read after the user
     * overlay, so the user's {@code databrowser.conf} may set it. Only meaningful in a config that
     * names {@code [bootstrap] single-instance}.
     */
    public static final String SINGLE_INSTANCE_PROPERTY = "cumba.single.instance";

    /**
     * Command-line argument that starts a separate instance (never stripped from the arguments).
     */
    public static final String NEW_INSTANCE_ARG = "--new-instance";

    /** The gate, replaceable by a test; {@code null} means the real {@link InstanceGate}. */
    private static @Nullable Gate gateOverride;

    /** The single-instance gate as the launcher calls it. */
    @FunctionalInterface
    interface Gate
    {

        InstanceGate.Outcome enter(Path aScope, String[] aArgs);
    }

    private Launcher()
    {
    }


    /**
     * Replaces the gate (tests); {@code null} restores the real one.
     *
     * @param aGate
     *            the gate
     */
    static void setGate(@Nullable Gate aGate)
    {
        gateOverride = aGate;
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
        // Taken AFTER bootstrap.dir and BEFORE the bundled [properties]: the user overlay's
        // ${sys:...} must see the JVM's own properties and the command line, not the bundled
        // values - otherwise ${sys:k:-v} in the user file could never let a -D win.
        Properties snapshot = (Properties) System.getProperties().clone();

        Map<String, String> resolvedProperties = new LinkedHashMap<>();
        resolvedProperties.put(BOOTSTRAP_DIR_PROPERTY, baseDir.toString());
        VariableResolver resolver = new VariableResolver(System::getenv, System::getProperty,
                resolvedProperties);

        applyProperties(config.properties(), config.source(), resolver, resolvedProperties);

        ErrorMode errorMode = ErrorMode
                .fromProperty(System.getProperty(CLASSPATH_ERROR_MODE_PROPERTY));
        URL[] classpath = buildClasspath(config, resolver, baseDir, errorMode);
        String mainClassName = resolveMainClassName(config, resolver, protectionDomain);

        // The class path, error mode and main class are fixed above, so nothing the user overlay
        // sets can change what code runs.
        Optional<UserConfig.Location> userConfig = UserConfig.locate(config, baseDir,
                System::getenv, snapshot::getProperty);
        if (userConfig.isPresent())
        {
            applyUserConfig(config, userConfig.get(), snapshot, resolvedProperties);
        }
        else
        {
            // No overlay declared: nothing is published, so a stale value cannot claim one.
            System.clearProperty(UserConfig.APPLIED_PROPERTY);
            System.clearProperty(UserConfig.CANDIDATES_PROPERTY);
        }

        if (config.singleInstanceDir().isPresent() && !passGate(config, args, resolver))
        {
            // handed off: the primary has the files, this JVM exits without the application
            return;
        }

        ClassLoader loader = createClassLoader(classpath, config.classLoaderMode());
        Thread.currentThread().setContextClassLoader(loader);
        invokeMain(mainClassName, loader, args);
    }


    /**
     * The single-instance gate (the config names {@code single-instance}). Off (the property is
     * {@code false}, or {@value #NEW_INSTANCE_ARG} is given) publishes {@code OFF} and touches no
     * file, lock or socket. Whatever the gate throws unexpectedly - a Windows without Unix domain
     * sockets, a scope that is no path - publishes {@code EXTRA:error} and the start runs: the gate
     * is an optimisation, it never stops a start.
     *
     * @return {@code false} when the start was handed off and the launcher must return
     */
    private static boolean passGate(BootstrapConfig config, String[] args,
            VariableResolver resolver)
    {
        if (!Boolean.parseBoolean(System.getProperty(SINGLE_INSTANCE_PROPERTY, "true"))
                || List.of(args).contains(NEW_INSTANCE_ARG))
        {
            InstanceGate.publishOff();
            return true;
        }
        InstanceGate.Outcome outcome;
        try
        {
            Path scope = Path.of(
                    resolver.resolve(config.singleInstanceDir().orElseThrow(), config.source()));
            Gate gate = gateOverride;
            outcome = gate != null ? gate.enter(scope, args)
                    : InstanceGate.enter(scope, args, Path.of(System.getProperty("user.dir", ".")));
        }
        catch (RuntimeException e)
        {
            InstanceGate.publishError(e);
            return true;
        }
        if (outcome.kind() == InstanceGate.Kind.HANDED_OFF)
        {
            return false;
        }
        outcome.publish();
        return true;
    }


    private static void applyProperties(List<Map.Entry<String, String>> properties, Path source,
            VariableResolver resolver, Map<String, String> resolvedProperties)
    {
        for (Map.Entry<String, String> property : properties)
        {
            String value = resolver.resolve(property.getValue(), source);
            System.setProperty(property.getKey(), value);
            resolvedProperties.put(property.getKey(), value);
        }
    }


    /**
     * Applies the user overlay, if one was found, and publishes where the launcher looked. Overlay
     * values are set unconditionally, so a bare user entry wins over the bundled value and over a
     * {@code -D}; a {@code ${sys:...}} in it resolves against the start-of-launch snapshot, while a
     * bare {@code ${name}} still sees the bundled keys.
     */
    private static void applyUserConfig(BootstrapConfig config, UserConfig.Location location,
            Properties snapshot, Map<String, String> resolvedProperties)
    {
        Optional<Path> applied = location.applied();
        if (applied.isPresent())
        {
            Path overlay = applied.get();
            List<Map.Entry<String, String>> properties = ConfigParser.parseOverlay(
                    ConfigFiles.read(overlay, "user config",
                            "chosen by -D" + UserConfig.OVERRIDE_PROPERTY
                                    + " or [bootstrap] user-config in " + config.source()),
                    overlay);
            for (Map.Entry<String, String> property : properties)
            {
                if (UserConfig.isReserved(config, property.getKey()))
                {
                    throw new BootstrapException(overlay + ": '" + property.getKey()
                            + "' is reserved and cannot be set in a user configuration");
                }
            }
            VariableResolver overlayResolver = new VariableResolver(System::getenv,
                    snapshot::getProperty, resolvedProperties);
            applyProperties(properties, overlay, overlayResolver, resolvedProperties);
            System.setProperty(UserConfig.APPLIED_PROPERTY, overlay.toString());
        }
        else
        {
            System.clearProperty(UserConfig.APPLIED_PROPERTY);
        }
        System.setProperty(UserConfig.CANDIDATES_PROPERTY, location.joinedCandidates());
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
