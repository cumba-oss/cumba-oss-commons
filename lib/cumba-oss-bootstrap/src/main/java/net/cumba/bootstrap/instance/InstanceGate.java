package net.cumba.bootstrap.instance;

import java.awt.GraphicsEnvironment;
import java.awt.SplashScreen;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The single-instance gate of the launcher, entered only when the bundled config names
 * {@code [bootstrap] single-instance = <scope>} (the distribution's opt-in; the value is the
 * instance scope, the user's settings directory) and the mode is on. One instance per user and per
 * scope, regardless of the installed version:
 *
 * <ul>
 * <li>the lock {@code <scope>/instance/instance.lock} is free → <b>PRIMARY</b>: the stale endpoint
 * of a previous run is deleted (from now until the application writes the new one, clients see "no
 * endpoint" = "primary starting" and retry), the outcome is published and the launcher continues to
 * the application, which listens;</li>
 * <li>held → <b>CLIENT</b> ({@link HandOffClient}): deliver the files to the primary and exit
 * ({@link Kind#HANDED_OFF}), or become PRIMARY after its exit, or fall through to
 * {@link Kind#EXTRA} - a normal start without lock or listener.</li>
 * </ul>
 *
 * <p>
 * Published for the application as system properties: {@value #DIR_PROPERTY} (the instance
 * directory), {@value #LOCKED_PROPERTY} ({@code true} when this JVM holds the lock) and
 * {@value #OUTCOME_PROPERTY} ({@code PRIMARY}, {@code EXTRA:<reason>} or {@code OFF}). The outcome
 * is published on every gated start, so the application knows a gate ran and never opens the lock
 * file itself (closing a second channel on it could release the lock). Without the key nothing here
 * runs and nothing is published.
 */
public final class InstanceGate
{

    private static final System.Logger LOGGER = System.getLogger(InstanceGate.class.getName());

    /** Published: the instance directory ({@code <scope>/instance}); PRIMARY only. */
    public static final String DIR_PROPERTY = "bootstrap.instance.dir";

    /** Published: {@code true} when the launcher holds the instance lock; PRIMARY only. */
    public static final String LOCKED_PROPERTY = "bootstrap.instance.locked";

    /**
     * Published: how the gated start went - {@code PRIMARY}, {@code EXTRA:<reason>}, {@code OFF}.
     */
    public static final String OUTCOME_PROPERTY = "bootstrap.instance.outcome";

    /** The {@value #OUTCOME_PROPERTY} value of a switched-off gate. */
    public static final String OUTCOME_OFF = "OFF";

    /** The {@value #OUTCOME_PROPERTY} value of the primary. */
    public static final String OUTCOME_PRIMARY = "PRIMARY";

    /** The {@value #OUTCOME_PROPERTY} prefix of a separate instance; the reason follows. */
    public static final String OUTCOME_EXTRA_PREFIX = "EXTRA:";

    /** How a gated start went. */
    public enum Kind
    {

        /** This JVM holds the lock and continues to the application. */
        PRIMARY,

        /** The files reached the primary; this JVM exits. */
        HANDED_OFF,

        /** A separate instance: no lock, no listener. */
        EXTRA,

        /** The gate is switched off for this start. */
        OFF
    }


    /**
     * The outcome of the gate.
     *
     * @param kind
     *            the kind
     * @param reason
     *            for {@link Kind#EXTRA}: why; else empty
     * @param instanceDir
     *            the instance directory
     */
    public record Outcome(Kind kind, String reason, Path instanceDir)
    {

        /** The {@value #OUTCOME_PROPERTY} value. */
        public String token()
        {
            return switch (kind)
            {
            case PRIMARY -> OUTCOME_PRIMARY;
            case EXTRA -> OUTCOME_EXTRA_PREFIX + reason;
            case OFF -> OUTCOME_OFF;
            case HANDED_OFF -> "HANDED_OFF";
            };
        }


        /** Publishes the system properties for the application (nothing for a hand-off). */
        public void publish()
        {
            if (kind == Kind.HANDED_OFF)
            {
                return;
            }
            System.setProperty(OUTCOME_PROPERTY, token());
            if (kind == Kind.PRIMARY)
            {
                System.setProperty(DIR_PROPERTY, instanceDir.toString());
                System.setProperty(LOCKED_PROPERTY, "true");
            }
        }
    }


    /**
     * The seams, so every branch runs on a Linux build host and every time-out on a fake clock.
     *
     * @param clock
     *            time and sleeping
     * @param lockProbe
     *            the lock
     * @param dirCheck
     *            the owner-only directory checks
     * @param platform
     *            the operating system
     * @param grant
     *            the foreground grant
     * @param watchdog
     *            the close watchdog over the sockets
     * @param splashCloser
     *            closes this JVM's splash screen (called on the server's hello back)
     */
    public record Deps(Clock clock, LockProbe lockProbe, DirCheck dirCheck, Platform platform,
            ForegroundGrant grant, CloseWatchdog watchdog, Runnable splashCloser)
    {

        /** The real seams. */
        public static Deps defaults()
        {
            Platform platform = Platform.current();
            return new Deps(Clock.SYSTEM, new InstanceLock(), DirCheck.DEFAULT, platform,
                    ForegroundGrant.forPlatform(platform), CloseWatchdog.daemon(),
                    InstanceGate::closeSplashScreen);
        }
    }

    private InstanceGate()
    {
    }


    /** Publishes {@value #OUTCOME_PROPERTY}{@code =OFF}: a gate ran and chose nothing. */
    public static void publishOff()
    {
        System.setProperty(OUTCOME_PROPERTY, OUTCOME_OFF);
    }


    /**
     * Enters the gate with the real seams.
     *
     * @param aScope
     *            the instance scope (the user settings directory)
     * @param aArgs
     *            the command line; the arguments naming existing files or directories are the paths
     *            to hand off, resolved against {@code aUserDir}
     * @param aUserDir
     *            this process's working directory
     * @return the outcome; {@link Kind#HANDED_OFF} means the launcher must return without starting
     *         the application
     */
    public static Outcome enter(Path aScope, String[] aArgs, Path aUserDir)
    {
        return enter(aScope, aArgs, aUserDir, Deps.defaults());
    }


    /**
     * Enters the gate.
     *
     * @param aScope
     *            the instance scope
     * @param aArgs
     *            the command line
     * @param aUserDir
     *            this process's working directory
     * @param aDeps
     *            the seams
     * @return the outcome
     */
    public static Outcome enter(Path aScope, String[] aArgs, Path aUserDir, Deps aDeps)
    {
        Path instanceDir = aScope.resolve(Endpoint.DIR_NAME);
        String why = aDeps.dirCheck().ensureOwnerOnly(instanceDir);
        if (why != null)
        {
            LOGGER.log(Level.INFO, "Single instance: instance directory refused (" + why
                    + "); starting as a separate instance");
            return new Outcome(Kind.EXTRA, "dir-check", instanceDir);
        }
        List<String> paths;
        try
        {
            paths = pathsOf(aArgs, aUserDir);
        }
        catch (IllegalArgumentException e)
        {
            String reason = String.valueOf(e.getMessage());
            LOGGER.log(Level.INFO,
                    "Single instance: " + reason + "; starting as a separate instance");
            return new Outcome(Kind.EXTRA, reason, instanceDir);
        }
        Path lockFile = instanceDir.resolve(Endpoint.LOCK_FILE_NAME);
        return switch (aDeps.lockProbe().tryLock(lockFile))
        {
        case ACQUIRED ->
        {
            deleteStaleEndpoint(instanceDir.resolve(Endpoint.FILE_NAME));
            yield new Outcome(Kind.PRIMARY, "", instanceDir);
        }
        case UNSUPPORTED -> new Outcome(Kind.EXTRA, "no-lock-support", instanceDir);
        case HELD -> new HandOffClient(instanceDir, aDeps).run(paths);
        };
    }


    /**
     * The paths an argument list names: every argument not starting with {@code --}, resolved
     * against the working directory, that is an existing file or directory.
     *
     * @param aArgs
     *            the command line
     * @param aUserDir
     *            the working directory
     * @return the absolute paths, in order
     * @throws IllegalArgumentException
     *             when the list exceeds the protocol's limits (a normal start opens them all)
     */
    static List<String> pathsOf(String[] aArgs, Path aUserDir)
    {
        return pathsOf(aArgs, aUserDir, p -> Files.isRegularFile(p) || Files.isDirectory(p));
    }


    /** {@link #pathsOf(String[], Path)} with the existence test as a seam (the length limit). */
    static List<String> pathsOf(String[] aArgs, Path aUserDir, Predicate<Path> aExists)
    {
        List<String> paths = new ArrayList<>();
        for (String arg : aArgs)
        {
            if (arg.startsWith("--"))
            {
                continue;
            }
            Path p;
            try
            {
                p = aUserDir.resolve(arg).toAbsolutePath().normalize();
            }
            catch (RuntimeException e)
            {
                continue;
            }
            if (!aExists.test(p))
            {
                continue;
            }
            String s = p.toString();
            if (s.getBytes(StandardCharsets.UTF_8).length > WireCodec.MAX_PATH_BYTES)
            {
                throw new IllegalArgumentException("path-too-long");
            }
            paths.add(s);
        }
        if (paths.size() > WireCodec.MAX_PATHS)
        {
            throw new IllegalArgumentException("too-many-paths");
        }
        return paths;
    }


    /**
     * Deletes the previous run's endpoint right after the lock was taken. A failed delete (Windows:
     * another process holds the file open without delete sharing) is logged and ignored: a client
     * reading the old endpoint fails fast at the old socket or gets {@code DENIED token}, and the
     * application replaces the file atomically.
     */
    static void deleteStaleEndpoint(Path aEndpointFile)
    {
        try
        {
            Files.deleteIfExists(aEndpointFile);
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.log(Level.INFO, "Single instance: cannot delete the previous endpoint "
                    + aEndpointFile + " (" + e + "); the application replaces it");
        }
    }


    /** Closes this JVM's splash screen, if it shows one; never touches AWT when headless. */
    static void closeSplashScreen()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            return;
        }
        SplashScreen splash = SplashScreen.getSplashScreen();
        if (splash != null)
        {
            splash.close();
        }
    }
}
