package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import net.cumba.bootstrap.itmain.TestMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapTest
{

    @TempDir
    Path tmp;

    private ClassLoader originalTccl;

    private Path testClasses;

    private Path out;

    private Path conf;

    @BeforeEach
    void setUp() throws URISyntaxException, IOException
    {
        originalTccl = Thread.currentThread().getContextClassLoader();
        testClasses = Path
                .of(TestMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        out = tmp.resolve("out.txt");
        conf = tmp.resolve("cumba-oss-bootstrap.conf");
        Files.write(conf,
                List.of("[bootstrap]", "main-class = net.cumba.bootstrap.itmain.TestMain",
                        "[properties]", "launcher.test.out = " + out, "foo = baz", "[classpath]",
                        testClasses.toString()));
    }


    @AfterEach
    void tearDown()
    {
        Thread.currentThread().setContextClassLoader(originalTccl);
        System.clearProperty("foo");
        System.clearProperty("launcher.test.out");
        System.clearProperty(Launcher.BOOTSTRAP_DIR_PROPERTY);
        System.clearProperty(ConfigLocator.CONFIG_OVERRIDE_PROPERTY);
    }


    @Test
    void runLoadsConfigAndLaunches() throws Exception
    {
        UnaryOperator<String> sys = key -> ConfigLocator.CONFIG_OVERRIDE_PROPERTY.equals(key)
                ? conf.toString()
                : null;
        Bootstrap.run(new String[]
        {
                "a", "b"
        }, getClass().getProtectionDomain(), sys);

        List<String> recorded = Files.readAllLines(out);
        assertTrue(recorded.contains("args=a|b"), () -> recorded.toString());
        assertTrue(recorded.contains("foo=baz"), () -> recorded.toString());
    }


    @Test
    void mainHappyPathDoesNotExit() throws Exception
    {
        System.setProperty(ConfigLocator.CONFIG_OVERRIDE_PROPERTY, conf.toString());
        Bootstrap.main(new String[]
        {
                "z"
        });

        List<String> recorded = Files.readAllLines(out);
        assertTrue(recorded.contains("args=z"), () -> recorded.toString());
    }


    @Test
    void runFailsWhenConfigMissing()
    {
        Path missing = tmp.resolve("nope.conf");
        UnaryOperator<String> sys = key -> ConfigLocator.CONFIG_OVERRIDE_PROPERTY.equals(key)
                ? missing.toString()
                : null;
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Bootstrap.run(new String[] {}, getClass().getProtectionDomain(), sys));
        assertTrue(ex.getMessage().contains("not found"));
    }


    @Test
    void runFailsWhenLaunchedFromNonJarWithoutOverride()
    {
        UnaryOperator<String> sys = _ -> null;
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Bootstrap.run(new String[] {}, getClass().getProtectionDomain(), sys));
        assertTrue(ex.getMessage().contains("not loaded from a .jar"));
    }
}
