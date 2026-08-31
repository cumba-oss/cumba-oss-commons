package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertThrows(BootstrapException.class, () -> Launcher.launch(cfg, new String[] {}, pd));
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
}
