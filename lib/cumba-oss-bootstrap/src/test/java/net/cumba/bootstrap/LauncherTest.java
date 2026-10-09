package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.cumba.bootstrap.instance.InstanceGate;
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
        for (String key : List.of("corej.rules.dir", "cumba.lib.dir", "launcher.test.home",
                UserConfig.OVERRIDE_PROPERTY, UserConfig.APPLIED_PROPERTY,
                UserConfig.CANDIDATES_PROPERTY, Launcher.SINGLE_INSTANCE_PROPERTY,
                InstanceGate.DIR_PROPERTY, InstanceGate.LOCKED_PROPERTY,
                InstanceGate.OUTCOME_PROPERTY))
        {
            System.clearProperty(key);
        }
        Launcher.setGate(null);
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
        assertTrue(recorded.contains("tccl=ChildFirstClassLoader"), recorded::toString);
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

    // ---- user overlay -------------------------------------------------------------------------

    private static final String USER_CANDIDATE = "${sys:launcher.test.home}/user.conf";

    private Path userHome() throws IOException
    {
        Path home = Files.createDirectories(tmp.resolve("home"));
        System.setProperty("launcher.test.home", home.toString());
        return home;
    }


    private Path userFile(String... lines) throws IOException
    {
        return Files.write(userHome().resolve("user.conf"), List.of(lines));
    }


    private BootstrapConfig overlayConfig(List<Map.Entry<String, String>> extra,
            List<String> classpath, Set<String> reserved)
    {
        List<Map.Entry<String, String>> properties = new java.util.ArrayList<>(
                List.of(Map.entry("launcher.test.out", out.toString()), Map.entry("foo", "bar")));
        properties.addAll(extra);
        return new BootstrapConfig(confPath, Optional.of(TARGET), ClassLoaderMode.PARENT_FIRST,
                properties, classpath, List.of(USER_CANDIDATE), reserved);
    }


    private BootstrapConfig overlayConfig(Map.Entry<String, String> extra)
    {
        return overlayConfig(List.of(extra), List.of(testClasses.toString()), Set.of());
    }


    private void launch(BootstrapConfig cfg) throws Exception
    {
        Launcher.launch(cfg, new String[] {}, getClass().getProtectionDomain());
    }


    @Test
    void aBareUserValueBeatsTheBundledValueAndADashD() throws Exception
    {
        System.setProperty("foo", "from-command-line");
        Path user = userFile("[properties]", "foo = from-user");

        launch(overlayConfig(List.of(), List.of(testClasses.toString()), Set.of()));

        assertTrue(Files.readAllLines(out).contains("foo=from-user"),
                () -> "the user value must win (D1)");
        assertEquals(user.toString(), System.getProperty(UserConfig.APPLIED_PROPERTY));
        assertEquals(user.toString(), System.getProperty(UserConfig.CANDIDATES_PROPERTY));
    }


    @Test
    void sysInTheUserFileSeesTheSnapshotNotTheBundledValue() throws Exception
    {
        // H2: with the bundled value visible, ${sys:k:-v} would always return it and v never.
        userFile("[properties]", "corej.rules.dir = ${sys:corej.rules.dir:-/u}");

        launch(overlayConfig(Map.entry("corej.rules.dir", "${sys:corej.rules.dir:-/bundled}")));

        assertEquals("/u", System.getProperty("corej.rules.dir"));
    }


    @Test
    void sysInTheUserFileLetsADashDWin() throws Exception
    {
        System.setProperty("corej.rules.dir", "/d");
        userFile("[properties]", "corej.rules.dir = ${sys:corej.rules.dir:-/u}");

        // A PLAIN bundled value: with ${sys:...} there the -D would win in the bundled file already
        // and this could not tell a snapshot lookup from a skipped overlay.
        launch(overlayConfig(Map.entry("corej.rules.dir", "/bundled")));

        assertEquals("/d", System.getProperty("corej.rules.dir"));
    }


    @Test
    void aBareReferenceInTheUserFileSeesTheBundledKeys() throws Exception
    {
        // L6: this is why '${k:-v}' in a user file keeps the bundled value.
        userFile("[properties]", "foo = ${foo}-and-user", "bootstrap.seen = ${bootstrap.dir}");

        launch(overlayConfig(List.of(), List.of(testClasses.toString()), Set.of()));

        List<String> recorded = Files.readAllLines(out);
        assertTrue(recorded.contains("foo=bar-and-user"), recorded::toString);
        assertEquals(tmp.toAbsolutePath().normalize().toString(),
                System.getProperty("bootstrap.seen"));
        System.clearProperty("bootstrap.seen");
    }


    @Test
    void theUserFileCannotChangeTheClasspath() throws Exception
    {
        // H1: the overlay is applied after the class path is built, so moving cumba.lib.dir to a
        // directory holding a jar must not load that jar - the value only reaches the application.
        Path markerDir = Files.createDirectories(tmp.resolve("marker"));
        Path marker = markerDir.resolve("marker.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(marker)))
        {
            jar.putNextEntry(new JarEntry("marker.txt"));
            jar.closeEntry();
        }
        Files.createDirectories(tmp.resolve("lib"));
        userFile("[properties]", "cumba.lib.dir = " + markerDir);

        launch(overlayConfig(List.of(Map.entry("cumba.lib.dir", "${bootstrap.dir}/lib")),
                List.of(testClasses.toString(), "${cumba.lib.dir}/*.jar"), Set.of()));

        // The launcher's loader, owned by nobody once the target returned: close it here.
        try (URLClassLoader loader = assertInstanceOf(URLClassLoader.class,
                Thread.currentThread().getContextClassLoader()))
        {
            assertEquals(List.of(testClasses.toUri().toURL()), List.of(loader.getURLs()));
        }
        assertEquals(markerDir.toString(), System.getProperty("cumba.lib.dir"));
        assertEquals(tmp.toAbsolutePath().normalize().toString(),
                System.getProperty(Launcher.BOOTSTRAP_DIR_PROPERTY));
    }


    @Test
    void aBuiltInReservedKeyInTheUserFileFails() throws Exception
    {
        Path user = userFile("[properties]", "classpath.error.mode = ERROR");
        BootstrapConfig cfg = overlayConfig(List.of(), List.of(testClasses.toString()), Set.of());

        BootstrapException ex = assertThrows(BootstrapException.class, () -> launch(cfg));
        assertEquals(user + ": 'classpath.error.mode' is reserved and cannot be set in a user"
                + " configuration", ex.getMessage());
        assertFalse(Files.exists(out), "the target must not run");
    }


    @Test
    void aReservedKeyDeclaredByTheConfigFails() throws Exception
    {
        userFile("[properties]", "foo = fine", "cumba.app.dir = /elsewhere");
        BootstrapConfig cfg = overlayConfig(List.of(), List.of(testClasses.toString()),
                Set.of("cumba.app.dir"));

        BootstrapException ex = assertThrows(BootstrapException.class, () -> launch(cfg));
        assertTrue(ex.getMessage().contains("'cumba.app.dir' is reserved"), ex::getMessage);
        assertEquals("bar", System.getProperty("foo"), "nothing of a rejected file is applied");
    }


    @Test
    void aClasspathSectionInTheUserFileFailsNamingTheUserFile() throws Exception
    {
        Path user = userFile("[classpath]", "/evil.jar");
        BootstrapConfig cfg = overlayConfig(List.of(), List.of(testClasses.toString()), Set.of());

        BootstrapException ex = assertThrows(BootstrapException.class, () -> launch(cfg));
        assertTrue(ex.getMessage().startsWith(user + ":1: "), ex::getMessage);
    }


    @Test
    void anUnresolvableUserValueNamesTheUserFile() throws Exception
    {
        Path user = userFile("[properties]", "foo = ${sys:launcher.test.nope}");
        BootstrapConfig cfg = overlayConfig(List.of(), List.of(testClasses.toString()), Set.of());

        BootstrapException ex = assertThrows(BootstrapException.class, () -> launch(cfg));
        assertTrue(ex.getMessage().startsWith(user + ": unresolved variable"), ex::getMessage);
    }


    @Test
    void noUserFilePublishesWhereItLookedAndClearsApplied() throws Exception
    {
        Path home = userHome();
        System.setProperty(UserConfig.APPLIED_PROPERTY, "stale");

        launch(overlayConfig(List.of(), List.of(testClasses.toString()), Set.of()));

        assertTrue(Files.readAllLines(out).contains("foo=bar"));
        assertNull(System.getProperty(UserConfig.APPLIED_PROPERTY));
        assertEquals(home.resolve("user.conf").toString(),
                System.getProperty(UserConfig.CANDIDATES_PROPERTY));
    }


    @Test
    void theSnapshotIsTakenAfterBootstrapDirIsSet() throws Exception
    {
        // Section 3.2 step 1: ${sys:bootstrap.dir} must work in the user file and in a candidate
        // exactly as in the bundled file. A snapshot taken before bootstrap.dir is seeded fails
        // both: the candidate below would be skipped and the entry would not resolve.
        Files.write(tmp.resolve("portable.conf"),
                List.of("[properties]", "portable.seen = ${sys:bootstrap.dir}"));
        BootstrapConfig cfg = new BootstrapConfig(confPath, Optional.of(TARGET),
                ClassLoaderMode.PARENT_FIRST,
                List.of(Map.entry("launcher.test.out", out.toString()), Map.entry("foo", "bar")),
                List.of(testClasses.toString()), List.of("${sys:bootstrap.dir}/portable.conf"),
                Set.of());
        try
        {
            launch(cfg);

            assertEquals(tmp.toAbsolutePath().normalize().toString(),
                    System.getProperty("portable.seen"));
        }
        finally
        {
            System.clearProperty("portable.seen");
        }
    }


    @Test
    void aConfigWithoutCandidatesPublishesNothingAndIgnoresTheOverride() throws Exception
    {
        Path user = userFile("[properties]", "foo = from-user");
        System.setProperty(UserConfig.OVERRIDE_PROPERTY, user.toString());
        // Stale values from a -D must not survive to claim an overlay that was never applied.
        System.setProperty(UserConfig.APPLIED_PROPERTY, "stale");
        System.setProperty(UserConfig.CANDIDATES_PROPERTY, "stale");

        launch(targetConfig(ClassLoaderMode.PARENT_FIRST));

        assertTrue(Files.readAllLines(out).contains("foo=bar"));
        assertNull(System.getProperty(UserConfig.APPLIED_PROPERTY));
        assertNull(System.getProperty(UserConfig.CANDIDATES_PROPERTY));
    }


    @Test
    void theOverrideIsReadFromTheCommandLine() throws Exception
    {
        Path other = Files.write(tmp.resolve("other.conf"),
                List.of("[properties]", "foo = from-override"));
        userFile("[properties]", "foo = from-user");
        System.setProperty(UserConfig.OVERRIDE_PROPERTY, other.toString());

        launch(overlayConfig(List.of(), List.of(testClasses.toString()), Set.of()));

        assertTrue(Files.readAllLines(out).contains("foo=from-override"));
        assertEquals(other.toString(), System.getProperty(UserConfig.APPLIED_PROPERTY));
    }


    @Test
    void theOverlayIsReadOnlyAfterTheMainClassIsResolved() throws Exception
    {
        // A config that cannot start must report that, not a problem in the user file.
        userFile("[classpath]", "/evil.jar");
        BootstrapConfig cfg = new BootstrapConfig(confPath, Optional.empty(),
                ClassLoaderMode.PARENT_FIRST, List.of(), List.of(), List.of(USER_CANDIDATE),
                Set.of());
        ProtectionDomain pd = new ProtectionDomain(null, null);

        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Launcher.launch(cfg, new String[] {}, pd));
        assertTrue(ex.getMessage().contains("no target main class"), ex::getMessage);
    }


    @Test
    void thePublishedPropertyNamesAreAContract()
    {
        // Applications spell these names to log where the user overlay came from (the data
        // browser's ViewerMain, pinned equal by a test in cumba-databrowser-launch). Renaming one
        // is a breaking change for every such application, not a refactoring.
        assertEquals("bootstrap.user.config", Launcher.USER_CONFIG_OVERRIDE_PROPERTY);
        assertEquals("bootstrap.user.config.applied", Launcher.USER_CONFIG_APPLIED_PROPERTY);
        assertEquals("bootstrap.user.config.candidates", Launcher.USER_CONFIG_CANDIDATES_PROPERTY);
    }

    // ---- the single-instance gate ---------------------------------------------------------

    /** A gate that records its calls and answers a fixed outcome. */
    private static final class RecordingGate implements Launcher.Gate
    {

        final List<Path> scopes = new java.util.ArrayList<>();

        final List<List<String>> args = new java.util.ArrayList<>();

        private final InstanceGate.Outcome outcome;

        RecordingGate(InstanceGate.Kind aKind, String aReason)
        {
            outcome = new InstanceGate.Outcome(aKind, aReason, Path.of("unused"));
        }


        @Override
        public InstanceGate.Outcome enter(Path aScope, String[] aArgs)
        {
            scopes.add(aScope);
            args.add(List.of(aArgs));
            return outcome;
        }
    }

    private BootstrapConfig gatedConfig(String aScope)
    {
        return new BootstrapConfig(confPath, Optional.of(TARGET), ClassLoaderMode.PARENT_FIRST,
                List.of(Map.entry("launcher.test.out", out.toString())),
                List.of(testClasses.toString()), List.of(), Set.of(), Optional.of(aScope));
    }


    @Test
    void withoutTheKeyTheGateIsNeverTouchedAndNothingIsPublished() throws Exception
    {
        RecordingGate gate = new RecordingGate(InstanceGate.Kind.PRIMARY, "");
        Launcher.setGate(gate);
        Launcher.launch(targetConfig(ClassLoaderMode.PARENT_FIRST), new String[]
        {
                "a.csv"
        }, pd());
        assertEquals(List.of(), gate.scopes, "no key: the gate is not entered");
        assertNull(System.getProperty(InstanceGate.OUTCOME_PROPERTY), "nothing published");
        assertEquals("args=a.csv", Files.readAllLines(out).get(0), "the application ran");
    }


    @Test
    void theScopeIsResolvedAndThePrimaryContinuesToTheApplication() throws Exception
    {
        System.setProperty("launcher.test.home", tmp.toString());
        RecordingGate gate = new RecordingGate(InstanceGate.Kind.PRIMARY, "");
        Launcher.setGate(gate);
        Launcher.launch(gatedConfig("${sys:launcher.test.home}/.settings"), new String[]
        {
                "a.csv", "--new-window"
        }, pd());
        assertEquals(List.of(tmp.resolve(".settings")), gate.scopes);
        assertEquals(List.of(List.of("a.csv", "--new-window")), gate.args,
                "the arguments reach the gate as they are");
        assertEquals("PRIMARY", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertEquals("args=a.csv|--new-window", Files.readAllLines(out).get(0),
                "and the application too, with the arguments untouched");
    }


    @Test
    void aHandOffReturnsWithoutStartingTheApplication() throws Exception
    {
        RecordingGate gate = new RecordingGate(InstanceGate.Kind.HANDED_OFF, "");
        Launcher.setGate(gate);
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        Launcher.launch(gatedConfig(tmp.toString()), new String[]
        {
                "a.csv"
        }, pd());
        assertEquals(1, gate.scopes.size());
        assertFalse(Files.exists(out), "the target main never ran");
        assertSame(before, Thread.currentThread().getContextClassLoader(),
                "no class loader was created");
        assertNull(System.getProperty(InstanceGate.OUTCOME_PROPERTY),
                "a hand-off publishes nothing: this JVM exits");
    }


    @Test
    void anExtraStartPublishesItsOutcomeAndRuns() throws Exception
    {
        RecordingGate gate = new RecordingGate(InstanceGate.Kind.EXTRA, "timeout");
        Launcher.setGate(gate);
        Launcher.launch(gatedConfig(tmp.toString()), new String[0], pd());
        assertEquals("EXTRA:timeout", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertNull(System.getProperty(InstanceGate.LOCKED_PROPERTY));
        assertTrue(Files.exists(out), "the application ran as a separate instance");
    }


    @Test
    void theSwitchAndTheArgumentTurnTheGateOff() throws Exception
    {
        RecordingGate gate = new RecordingGate(InstanceGate.Kind.PRIMARY, "");
        Launcher.setGate(gate);
        // the property, as the user overlay sets it (applied before the gate)
        BootstrapConfig off = new BootstrapConfig(confPath, Optional.of(TARGET),
                ClassLoaderMode.PARENT_FIRST,
                List.of(Map.entry("launcher.test.out", out.toString()),
                        Map.entry(Launcher.SINGLE_INSTANCE_PROPERTY, "false")),
                List.of(testClasses.toString()), List.of(), Set.of(), Optional.of(tmp.toString()));
        Launcher.launch(off, new String[0], pd());
        assertEquals(List.of(), gate.scopes, "switched off: the gate is not entered");
        assertEquals("OFF", System.getProperty(InstanceGate.OUTCOME_PROPERTY),
                "but the application learns a gate ran and must not lock itself");
        assertFalse(Files.exists(tmp.resolve("instance")), "no file was created");

        System.clearProperty(InstanceGate.OUTCOME_PROPERTY);
        System.clearProperty(Launcher.SINGLE_INSTANCE_PROPERTY);
        Files.delete(out);
        Launcher.launch(gatedConfig(tmp.toString()), new String[]
        {
                "--new-instance", "a.csv"
        }, pd());
        assertEquals(List.of(), gate.scopes);
        assertEquals("OFF", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertEquals("args=--new-instance|a.csv", Files.readAllLines(out).get(0),
                "the argument is never stripped");
    }


    private static ProtectionDomain pd()
    {
        return LauncherTest.class.getProtectionDomain();
    }
}
