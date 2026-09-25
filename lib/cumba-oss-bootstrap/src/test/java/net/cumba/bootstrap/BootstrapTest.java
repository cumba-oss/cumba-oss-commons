package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private Path out;

    private Path conf;

    @BeforeEach
    void setUp() throws URISyntaxException, IOException
    {
        originalTccl = Thread.currentThread().getContextClassLoader();
        Path testClasses = Path
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
        assertTrue(recorded.contains("args=a|b"), recorded::toString);
        assertTrue(recorded.contains("foo=baz"), recorded::toString);
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
        assertTrue(recorded.contains("args=z"), recorded::toString);
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


    @Test
    void errorDialogIsOffUnlessTheLauncherOptsIn()
    {
        List<String> shown = new ArrayList<>();
        assertFalse(Bootstrap.showErrorDialogIfEnabled("boom", _ -> null, () -> false, shown::add));
        assertFalse(
                Bootstrap.showErrorDialogIfEnabled("boom", _ -> "false", () -> false, shown::add));
        assertTrue(shown.isEmpty());
    }


    @Test
    void errorDialogIsShownWhenOptedInWithADisplay()
    {
        List<String> shown = new ArrayList<>();
        UnaryOperator<String> sys = k -> Bootstrap.ERROR_DIALOG_PROPERTY.equals(k) ? "true" : null;
        assertTrue(Bootstrap.showErrorDialogIfEnabled("boom", sys, () -> false, shown::add));
        assertEquals(List.of("boom"), shown);
    }


    @Test
    void errorDialogIsNotShownHeadless()
    {
        List<String> shown = new ArrayList<>();
        assertFalse(
                Bootstrap.showErrorDialogIfEnabled("boom", _ -> "true", () -> true, shown::add));
        assertTrue(shown.isEmpty());
    }


    @Test
    void aFailingDialogNeverEscapes()
    {
        assertFalse(Bootstrap.showErrorDialogIfEnabled("boom", _ -> "true", () -> false, _ ->
        {
            throw new UnsatisfiedLinkError("no awt");
        }));
    }


    @Test
    void errorDialogTextCarriesTheFailure()
    {
        String text = Bootstrap.errorDialogText("config broken");
        assertTrue(text.contains("config broken"));
        // An error in the user overlay must not send the user only to the file beside the jar.
        assertTrue(text.contains("from your user configuration file"), text);
    }


    @Test
    void errorTextAreaWrapsAndIsReadOnly()
    {
        var text = Bootstrap.errorTextArea("/a/very/long/path/databrowser.conf:9: broken");
        assertTrue(text.getLineWrap());
        assertTrue(text.getWrapStyleWord());
        assertFalse(text.isEditable());
        assertTrue(text.getText().contains("databrowser.conf:9: broken"));
    }


    @Test
    void aConfigThatIsNotUtf8IsReportedAsSuch() throws Exception
    {
        // "ANSI" / Windows-1252 with an umlaut: 0xE4 is not valid UTF-8.
        Files.write(conf, "[bootstrap]\n# J\u00e4ger\nmain-class = a.B\n"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        UnaryOperator<String> sys = key -> ConfigLocator.CONFIG_OVERRIDE_PROPERTY.equals(key)
                ? conf.toString()
                : null;
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> Bootstrap.run(new String[0], getClass().getProtectionDomain(), sys));
        assertTrue(ex.getMessage().contains("is not UTF-8 text"), ex::getMessage);
    }


    @Test
    void anInterruptedDialogIsReportedAsNotShown()
    {
        try
        {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> Bootstrap.showErrorDialog("boom", _ ->
                    {
                        throw new InterruptedException();
                    }));
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag is restored");
            assertTrue(ex.getMessage().contains("interrupted"));
            assertFalse(Bootstrap.showErrorDialogIfEnabled("boom", _ -> "true", () -> false,
                    m -> Bootstrap.showErrorDialog(m, _ ->
                    {
                        throw new InterruptedException();
                    })));
        }
        finally
        {
            Thread.interrupted(); // clear the flag for the following tests
        }
    }


    @Test
    void aFailureInsideTheDialogBecomesUnchecked()
    {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Bootstrap.showErrorDialog("boom", _ ->
                {
                    throw new java.lang.reflect.InvocationTargetException(new AssertionError("x"));
                }));
        assertTrue(ex.getCause() instanceof AssertionError);
    }


    @Test
    void theDialogTaskIsHandedToTheEdtRunner()
    {
        List<Runnable> tasks = new ArrayList<>();
        Bootstrap.showErrorDialog("boom", tasks::add);
        assertEquals(1, tasks.size(), "exactly one task; it is not run headless here");
    }


    @Test
    void errorPaneIsAnErrorMessageWithTheWrappingText()
    {
        var pane = Bootstrap.errorPane("config broken");
        assertEquals(javax.swing.JOptionPane.ERROR_MESSAGE, pane.getMessageType());
        assertTrue(pane.getMessage() instanceof javax.swing.JTextArea);
    }
}
