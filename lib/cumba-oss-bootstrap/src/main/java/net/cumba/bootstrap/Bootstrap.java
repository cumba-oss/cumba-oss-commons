package net.cumba.bootstrap;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Entry point of the launcher jar. Locates and parses the sidecar config, then delegates to
 * {@link Launcher} to apply it and invoke the target program.
 *
 * <p>
 * A launcher-side failure ({@link BootstrapException}) prints a concise {@code bootstrap: ...}
 * message to stderr and exits with status {@code 2}. Exceptions thrown by the target program itself
 * propagate unchanged so the target controls its own exit status.
 *
 * <p>
 * A launcher without a console (a GUI launcher such as Launch4j's {@code gui} header or a jpackage
 * application) opts in to also seeing that message in a dialog by setting the system property
 * {@value #ERROR_DIALOG_PROPERTY}{@code =true}; without it, or without a display, nothing but
 * stderr is used. It is a system property, not a config key, because the failure to report is often
 * that the config itself cannot be read or parsed.
 */
public final class Bootstrap
{

    /** System property that makes a launcher-side failure also show a dialog; see the class doc. */
    public static final String ERROR_DIALOG_PROPERTY = "bootstrap.error.dialog";

    private Bootstrap()
    {
    }


    /**
     * Launcher entry point.
     *
     * @param args
     *            command-line arguments, passed through verbatim to the target {@code main}
     * @throws Exception
     *             any exception thrown by the target program (propagated as-is)
     */
    // A lambda, not GraphicsEnvironment::isHeadless: a method reference is linked when the argument
    // is evaluated, before the opt-in check, and would load java.desktop on every failed start.
    @SuppressWarnings("PMD.LambdaCanBeMethodReference")
    public static void main(String[] args) throws Exception
    {
        try
        {
            run(args, Bootstrap.class.getProtectionDomain(), System::getProperty);
        }
        catch (BootstrapException e)
        {
            System.err.println("bootstrap: " + e.getMessage());
            showErrorDialogIfEnabled(String.valueOf(e.getMessage()), System::getProperty,
                    () -> GraphicsEnvironment.isHeadless(),
                    message -> showErrorDialog(message, SwingUtilities::invokeAndWait));
            System.exit(2);
        }
    }


    /**
     * Shows a launcher-side failure in a dialog, if {@value #ERROR_DIALOG_PROPERTY} is {@code true}
     * and a display is available. Never throws: the message is already on stderr, and a dialog that
     * fails must not replace it with a different failure.
     *
     * @param message
     *            the failure, as printed to stderr
     * @param sysLookup
     *            maps a system property name to its value (or {@code null})
     * @param headless
     *            whether the JVM has no display
     * @param dialog
     *            shows the message
     * @return {@code true} if the dialog was shown
     */
    static boolean showErrorDialogIfEnabled(String message, UnaryOperator<String> sysLookup,
            BooleanSupplier headless, Consumer<String> dialog)
    {
        if (!Boolean.parseBoolean(sysLookup.apply(ERROR_DIALOG_PROPERTY))
                || headless.getAsBoolean())
        {
            return false;
        }
        try
        {
            dialog.accept(message);
            return true;
        }
        // Also Errors: a missing AWT native library surfaces as an UnsatisfiedLinkError or
        // AWTError, and either would otherwise escape main in place of the real failure.
        catch (RuntimeException | Error ex)
        {
            System.err.println("bootstrap: cannot show the error dialog: " + ex);
            return false;
        }
    }


    /** The dialog text: the failure, and where the configuration it usually concerns lives. */
    static String errorDialogText(String message)
    {
        return "The application could not be started:\n\n" + message
                + "\n\nThe launcher's configuration is the .conf file named above (normally the one"
                + " beside the launcher jar).";
    }


    /**
     * The dialog's message component: a wrapping, read-only text area. The message usually holds a
     * long file path, which a plain {@code JOptionPane} label renders on one line wider than the
     * screen; the text area wraps it, and lets the user select and copy it.
     */
    static JTextArea errorTextArea(String message)
    {
        JTextArea text = new JTextArea(errorDialogText(message), 0, 70);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setOpaque(false);
        return text;
    }

    /**
     * Runs a task on the event dispatch thread and waits: {@code SwingUtilities::invokeAndWait}.
     */
    @FunctionalInterface
    interface EdtRunner
    {

        void runAndWait(Runnable task) throws InterruptedException, InvocationTargetException;
    }

    /**
     * Shows the error dialog through {@code edt}; any failure becomes an unchecked exception, which
     * {@link #showErrorDialogIfEnabled} reports as "not shown".
     */
    static void showErrorDialog(String message, EdtRunner edt)
    {
        try
        {
            edt.runAndWait(() -> showOnTop(message));
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted before the error dialog was shown", e);
        }
        catch (InvocationTargetException e)
        {
            throw new IllegalStateException("error dialog failed", e.getCause());
        }
    }


    /** The option pane the dialog shows: an error icon and the wrapping message text. */
    static JOptionPane errorPane(String message)
    {
        return new JOptionPane(errorTextArea(message), JOptionPane.ERROR_MESSAGE);
    }


    private static void showOnTop(String message)
    {
        // An ownerless dialog has no taskbar entry, and Windows' foreground lock can open it behind
        // other windows while main() waits for it; keep it on top.
        JDialog dialog = errorPane(message).createDialog("Cannot start the application");
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        dialog.dispose();
    }


    /**
     * Locates, reads and applies the config, then launches the target. Throws rather than exiting,
     * so it is unit-testable; {@link #main(String[])} maps {@link BootstrapException} to an exit
     * code.
     *
     * @param args
     *            command-line arguments for the target {@code main}
     * @param protectionDomain
     *            the launcher's protection domain (config location + manifest fallback)
     * @param sysLookup
     *            maps a system property name to its value (or {@code null})
     * @throws Exception
     *             a {@link BootstrapException} on any launcher-side failure, or any exception
     *             thrown by the target program
     */
    static void run(String[] args, ProtectionDomain protectionDomain,
            UnaryOperator<String> sysLookup)
        throws Exception
    {
        Path configPath = ConfigLocator.locate(protectionDomain, sysLookup);
        List<String> lines = readConfig(configPath);
        BootstrapConfig config = ConfigParser.parse(lines, configPath);
        Launcher.launch(config, args, protectionDomain);
    }


    private static List<String> readConfig(Path configPath)
    {
        try
        {
            return Files.readAllLines(configPath, StandardCharsets.UTF_8);
        }
        catch (NoSuchFileException e)
        {
            throw new BootstrapException("bootstrap config not found: " + configPath
                    + " (override with -D" + ConfigLocator.CONFIG_OVERRIDE_PROPERTY + "=<path>)",
                    e);
        }
        catch (MalformedInputException e)
        {
            throw new BootstrapException("bootstrap config " + configPath + " is not UTF-8 text"
                    + " (save it as UTF-8; a byte-order mark is fine)", e);
        }
        catch (IOException e)
        {
            throw new BootstrapException(
                    "cannot read bootstrap config " + configPath + ": " + e.getMessage(), e);
        }
    }
}
