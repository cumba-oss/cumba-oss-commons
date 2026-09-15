package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.cumba.bootstrap.itmain.TestMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest
{

    private static final String TARGET = "net.cumba.bootstrap.itmain.TestMain";

    @TempDir
    Path tmp;

    private ClassLoader originalTccl;

    private Path testClasses;

    private Path out;

    private Path confPath;

    @BeforeEach
    void setUp() throws URISyntaxException
    {
        originalTccl = Thread.currentThread().getContextClassLoader();
        testClasses = Path
                .of(TestMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        out = tmp.resolve("out.txt");
        confPath = tmp.resolve("app.conf");
    }


    @AfterEach
    void tearDown()
    {
        Thread.currentThread().setContextClassLoader(originalTccl);
        System.clearProperty("foo");
        System.clearProperty("launcher.test.out");
        System.clearProperty(Launcher.BOOTSTRAP_DIR_PROPERTY);
        System.clearProperty(Launcher.CLASSPATH_ERROR_MODE_PROPERTY);
    }


    private BootstrapConfig config(Optional<String> mainClass, ClassLoaderMode mode,
            List<Map.Entry<String, String>> properties, List<String> classpath)
    {
        return new BootstrapConfig(confPath, mainClass, mode, properties, classpath);
    }


    private BootstrapConfig targetConfig(ClassLoaderMode mode)
    {
        return config(Optional.of(TARGET), mode,
                List.of(Map.entry("launcher.test.out", out.toString()), Map.entry("foo", "bar")),
                List.of(testClasses.toString()));
    }


    @Test
    void appliesPropertiesPassesArgsAndInvokesMain() throws Exception
    {
        Launcher.launch(targetConfig(ClassLoaderMode.PARENT_FIRST), new String[]
        {
                "hello", "world"
        }, getClass().getProtectionDomain());

        List<String> recorded = Files.readAllLines(out);
        String expectedDir = confPath.toAbsolutePath().normalize().getParent().toString();
        assertEquals(List.of("args=hello|world", "foo=bar", "bootstrapDir=" + expectedDir,
                "tccl=URLClassLoader"), recorded);
    }


    @Test
    void childFirstModeUsesChildFirstClassLoader() throws Exception
    {
        Launcher.launch(targetConfig(ClassLoaderMode.CHILD_FIRST), new String[] {},
                getClass().getProtectionDomain());

        List<String> recorded = Files.readAllLines(out);
        assertTrue(recorded.contains("tccl=ChildFirstClassLoader"), () -> recorded.toString());
    }


    @Test
    void targetExceptionPropagatesUnwrapped()
    {
        BootstrapConfig cfg = targetConfig(ClassLoaderMode.PARENT_FIRST);
        ProtectionDomain pd = getClass().getProtectionDomain();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Launcher.launch(cfg, new String[]
                {
                        "throw"
                }, pd));
        assertEquals("boom from target main", ex.getMessage());
    }


    @Test
    void targetErrorPropagatesUnwrapped()
    {
        BootstrapConfig cfg = targetConfig(ClassLoaderMode.PARENT_FIRST);
        ProtectionDomain pd = getClass().getProtectionDomain();
        AssertionError err = assertThrows(AssertionError.class,
                () -> Launcher.launch(cfg, new String[]
                {
                        "error"
                }, pd));
        assertEquals("error from target main", err.getMessage());
    }


    @Test
    void missingMainClassFails()
    {
        BootstrapConfig cfg = config(Optional.of("no.Such"), ClassLoaderMode.PARENT_FIRST,
                List.of(), List.of(testClasses.toString()));
        ProtectionDomain pd = getClass().getProtectionDomain();
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("not found"));
    }


    @Test
    void noMainClassAndNoManifestFails()
    {
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = new ProtectionDomain(null, null);
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"));
    }


    @Test
    void blankMainClassValueFallsThroughToError()
    {
        BootstrapConfig cfg = config(Optional.of("   "), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = new ProtectionDomain(null, null);
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        // Specifically the 'nothing declared anywhere' error. Keeping the blank value instead
        // would fail too — with 'target main class not found: ' — which reads as a classpath
        // problem and sends the operator looking in the wrong place.
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    /**
     * Builds a launcher jar carrying only a manifest, so the manifest main-class fallback can be
     * exercised against a real jar rather than a mock.
     */
    private Path launcherJar(String bootstrapMainClass) throws IOException
    {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (bootstrapMainClass != null)
        {
            manifest.getMainAttributes().putValue(Launcher.MANIFEST_MAIN_CLASS_ATTRIBUTE,
                    bootstrapMainClass);
        }
        Path jar = tmp.resolve("launcher.jar");
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jar), manifest))
        {
            // the manifest is the whole point; no entries needed
            jarOut.flush();
        }
        return jar;
    }


    private static ProtectionDomain domainAt(URL location)
    {
        return new ProtectionDomain(new CodeSource(location, (Certificate[]) null), null);
    }


    private BootstrapConfig configWithoutMainClass(Optional<String> mainClass)
    {
        return config(mainClass, ClassLoaderMode.PARENT_FIRST,
                List.of(Map.entry("launcher.test.out", out.toString()), Map.entry("foo", "bar")),
                List.of(testClasses.toString()));
    }


    @Test
    void manifestSuppliesTheMainClassWhenTheConfigOmitsIt() throws Exception
    {
        Path jar = launcherJar(TARGET);

        Launcher.launch(configWithoutMainClass(Optional.empty()), new String[]
        {
                "from-manifest"
        }, domainAt(jar.toUri().toURL()));

        assertEquals(List.of("args=from-manifest", "foo=bar",
                "bootstrapDir=" + confPath.toAbsolutePath().normalize().getParent(),
                "tccl=URLClassLoader"), Files.readAllLines(out));
    }


    @Test
    void aDeclaredButBlankMainClassIsTreatedAsOmitted() throws Exception
    {
        // README: a blank value means 'not configured', so the manifest fallback applies. Keeping
        // the blank string instead would try to load a class called "" and report it as missing.
        Path jar = launcherJar(TARGET);

        Launcher.launch(configWithoutMainClass(Optional.of("  ")), new String[]
        {
                "blank"
        }, domainAt(jar.toUri().toURL()));

        assertTrue(Files.readAllLines(out).contains("args=blank"),
                () -> "the manifest main class should have run");
    }


    @Test
    void configMainClassWinsOverTheManifest() throws Exception
    {
        Path jar = launcherJar("net.cumba.definitely.Absent");

        Launcher.launch(targetConfig(ClassLoaderMode.PARENT_FIRST), new String[]
        {
                "config-wins"
        }, domainAt(jar.toUri().toURL()));

        assertTrue(Files.readAllLines(out).contains("args=config-wins"),
                () -> "the config's main-class must not be overridden by the manifest");
    }


    @Test
    void launcherJarWithoutTheHeaderFails() throws IOException
    {
        Path jar = launcherJar(null);
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(jar.toUri().toURL());

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void launcherJarWithoutAManifestFails() throws IOException
    {
        Path jar = tmp.resolve("launcher.jar");
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jar)))
        {
            // no Manifest argument: the jar has no META-INF/MANIFEST.MF at all, which is a
            // different 'no header' case from a manifest that simply omits the attribute.
            jarOut.putNextEntry(new JarEntry("placeholder.txt"));
            jarOut.closeEntry();
        }
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(jar.toUri().toURL());

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void unreadableLauncherJarFallsThroughToTheClearError() throws IOException
    {
        Path notAJar = Files.writeString(tmp.resolve("launcher.jar"), "this is not a zip");
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(notAJar.toUri().toURL());

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void explodedDirectoryCodeSourceHasNoManifest() throws IOException
    {
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(tmp.toUri().toURL());

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void codeSourceWithoutALocationHasNoManifest()
    {
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(null);

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void nonFilesystemCodeSourceHasNoManifest() throws IOException
    {
        // A nested-jar loader reports a 'jar:' code source, for which Path.of throws the UNCHECKED
        // FileSystemNotFoundException. The manifest is only a fallback, so that must read as 'no
        // header' rather than escaping as a stack trace past Bootstrap's exit-2 contract.
        BootstrapConfig cfg = config(Optional.empty(), ClassLoaderMode.PARENT_FIRST, List.of(),
                List.of());
        ProtectionDomain pd = domainAt(URI.create("jar:file:/nowhere/app.jar!/").toURL());

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void invalidErrorModePropertyFails() throws IOException
    {
        System.setProperty(Launcher.CLASSPATH_ERROR_MODE_PROPERTY, "shout");
        BootstrapConfig cfg = targetConfig(ClassLoaderMode.PARENT_FIRST);
        ProtectionDomain pd = getClass().getProtectionDomain();
        assertThrows(BootstrapException.class, () -> Launcher.launch(cfg, new String[] {}, pd));
        Files.deleteIfExists(out);
    }


    @Test
    void launderUnwrapsAnExceptionFromTheTarget()
    {
        IllegalStateException cause = new IllegalStateException("from target");
        assertSame(cause, Launcher.launderTargetException(new InvocationTargetException(cause)));
    }


    @Test
    void launderRethrowsAnErrorFromTheTarget()
    {
        AssertionError cause = new AssertionError("from target");
        AssertionError thrown = assertThrows(AssertionError.class,
                () -> Launcher.launderTargetException(new InvocationTargetException(cause)));
        assertSame(cause, thrown);
    }


    @Test
    void launderKeepsTheWrapperWhenThereIsNoCause()
    {
        // Legal but exotic, and unreachable through Method.invoke: returning anything else here
        // (null above all) would turn a target failure into an NPE from the throw site.
        InvocationTargetException noCause = new InvocationTargetException(null);
        assertSame(noCause, Launcher.launderTargetException(noCause));
    }


    @Test
    void launderWrapsACauseThatIsNeitherExceptionNorError()
    {
        Throwable cause = new Throwable("a bare Throwable");
        Exception laundered = Launcher.launderTargetException(new InvocationTargetException(cause));

        assertInstanceOf(BootstrapException.class, laundered);
        assertSame(cause, laundered.getCause());
    }
}
