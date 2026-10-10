package net.cumba.bootstrap.instance;

import java.io.EOFException;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The client of the single-instance hand-off: the lock is held, so this start tries to deliver its
 * files to the running primary and exit. Every decision is on the injected {@link Clock}, the lock
 * on the {@link LockProbe}, the socket directory on the {@link DirCheck}, the foreground grant on
 * the {@link ForegroundGrant}; the real sockets are the JVM's.
 *
 * <p>
 * The loop, per round (at most {@link #MAX_ROUNDS}):
 * <ol>
 * <li><b>connect window</b> ({@link #CONNECT_WINDOW_MILLIS}): on every attempt the endpoint file is
 * re-read (missing = "primary starting"; {@code listener=none} read twice in a row = EXTRA); the
 * socket's directory is verified when it is not the instance directory; then connect and handshake.
 * A {@code DENIED token} (a stale endpoint) re-reads and retries; {@code DENIED version} /
 * {@code DENIED limit} end in EXTRA. On a missing endpoint or a refused socket the attempt also
 * tries the lock: free means the old primary died before it listened - this start is PRIMARY. While
 * the lock is held and <em>no</em> endpoint exists the primary is alive and still starting (class
 * loading, a virus scanner on a cold start), so that state waits up to {@link #START_WINDOW_MILLIS}
 * instead.</li>
 * <li>after the hello back the client closes its own splash screen, grants the foreground to the
 * server's pid, sends {@code OPEN} and reads replies: {@code BUSY STARTING} counts against the
 * {@link #START_CAP_MILLIS}, {@code BUSY QUEUED} (a modal dialog, the user's time) against nothing
 * but the reply gap; {@code ACCEPTED} = handed off; {@code CLOSING} or EOF (the primary died) = the
 * lock wait; {@code DENIED <reason>} (the primary refused the {@code OPEN}) = EXTRA at once.</li>
 * <li><b>lock wait</b> ({@link #LOCK_WAIT_MILLIS}): try the lock; a different token in the endpoint
 * means a third start took the lock first - back to the connect window (round + 1).</li>
 * </ol>
 * Any expiry falls through to EXTRA: a normal start without lock or listener - worst case two
 * windows, never a lost file.
 */
public final class HandOffClient
{

    private static final System.Logger LOGGER = System.getLogger(HandOffClient.class.getName());

    /**
     * How long a client keeps trying to connect while the lock is held and an endpoint exists that
     * does not answer (a refused socket, a stale token).
     */
    public static final long CONNECT_WINDOW_MILLIS = 10_000;

    /**
     * How long a client waits while the lock is held and no endpoint exists yet: the lock holder is
     * alive (the OS releases the lock of a dead process), it has not listened yet - a cold start.
     */
    public static final long START_WINDOW_MILLIS = 30_000;

    /** The pause between attempts (and between lock probes). */
    public static final long RETRY_MILLIS = 100;

    /** Most time between two replies (the watchdog closes the socket after it). */
    public static final long REPLY_GAP_MILLIS = 5_000;

    /** Most time a client waits on {@code BUSY STARTING} heartbeats. */
    public static final long START_CAP_MILLIS = 60_000;

    /** How long a client waits for the lock after {@code CLOSING} or EOF. */
    public static final long LOCK_WAIT_MILLIS = 30_000;

    /** Most rounds of connect window → lock wait → connect window. */
    public static final int MAX_ROUNDS = 3;

    /** What a round or an attempt ended in. */
    private enum Step
    {
        HANDED_OFF, PRIMARY, EXTRA, WAIT_LOCK, RETRY, CONNECT_FAILED, NEW_TOKEN
    }


    private record Result(Step step, String reason)
    {

        static Result of(Step aStep)
        {
            return new Result(aStep, "");
        }


        static Result extra(String aReason)
        {
            return new Result(Step.EXTRA, aReason);
        }
    }


    /** Thrown inside an attempt when the watchdog closed the channel. */
    private static final class ReplyTimeout extends IOException
    {

        private static final long serialVersionUID = 1L;

        ReplyTimeout(ClosedChannelException aCause)
        {
            super("no reply within " + REPLY_GAP_MILLIS + " ms", aCause);
        }
    }

    private final Path instanceDir;

    private final Path endpointFile;

    private final Path lockFile;

    private final InstanceGate.Deps deps;

    private @Nullable String lastToken;

    HandOffClient(Path aInstanceDir, InstanceGate.Deps aDeps)
    {
        instanceDir = aInstanceDir;
        endpointFile = aInstanceDir.resolve(Endpoint.FILE_NAME);
        lockFile = aInstanceDir.resolve(Endpoint.LOCK_FILE_NAME);
        deps = aDeps;
    }


    /**
     * Runs the hand-off.
     *
     * @param aPaths
     *            the absolute paths to deliver (empty = "come to the front")
     * @return {@link InstanceGate.Kind#HANDED_OFF}, {@link InstanceGate.Kind#PRIMARY} or
     *         {@link InstanceGate.Kind#EXTRA} with its reason
     */
    InstanceGate.Outcome run(List<String> aPaths)
    {
        try
        {
            for (int round = 0; round < MAX_ROUNDS; round++)
            {
                Result r = connectWindow(aPaths);
                if (r.step() != Step.WAIT_LOCK)
                {
                    return outcome(r);
                }
                Result w = lockWait();
                if (w.step() != Step.NEW_TOKEN)
                {
                    return outcome(w);
                }
                LOGGER.log(Level.INFO, "Single instance: a new primary took the lock during the"
                        + " wait; handing off to it (round " + (round + 2) + ")");
            }
            return extra("rounds");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return extra("interrupted");
        }
    }


    private InstanceGate.Outcome outcome(Result aResult)
    {
        return switch (aResult.step())
        {
        case HANDED_OFF -> new InstanceGate.Outcome(InstanceGate.Kind.HANDED_OFF, "", instanceDir);
        case PRIMARY -> new InstanceGate.Outcome(InstanceGate.Kind.PRIMARY, "", instanceDir);
        default -> extra(aResult.reason());
        };
    }


    private InstanceGate.Outcome extra(String aReason)
    {
        LOGGER.log(Level.INFO,
                "Single instance: no hand-off (" + aReason + "); starting as a separate instance");
        return new InstanceGate.Outcome(InstanceGate.Kind.EXTRA, aReason, instanceDir);
    }


    /** Step 1 of the class comment. */
    private Result connectWindow(List<String> aPaths) throws InterruptedException
    {
        long start = deps.clock().nanoTime();
        boolean noListenerBefore = false;
        while (true)
        {
            Optional<Endpoint> endpoint = Endpoint.read(endpointFile);
            boolean noListener = endpoint.isPresent() && endpoint.get().noListener();
            if (noListener && noListenerBefore)
            {
                return Result.extra("no-listener");
            }
            // a first listener=none may be the previous run's: a new primary deletes it right
            // after its lock, so it is read once more after a pause (and the lock is tried)
            noListenerBefore = noListener;
            boolean probeLock = endpoint.isEmpty() || noListener;
            if (endpoint.isPresent() && !noListener)
            {
                Result attempt = tryEndpoint(endpoint.get(), aPaths);
                if (attempt.step() == Step.CONNECT_FAILED)
                {
                    probeLock = true;
                }
                else if (attempt.step() != Step.RETRY)
                {
                    return attempt;
                }
            }
            boolean starting = false;
            if (probeLock)
            {
                LockProbe.Status lock = deps.lockProbe().tryLock(lockFile);
                if (lock == LockProbe.Status.ACQUIRED)
                {
                    InstanceGate.deleteStaleEndpoint(endpointFile);
                    return Result.of(Step.PRIMARY);
                }
                if (lock == LockProbe.Status.UNSUPPORTED)
                {
                    return Result.extra("no-lock-support");
                }
                // held, and nothing written yet: the primary lives and is still starting
                starting = endpoint.isEmpty();
            }
            Result expired = expired(start, starting);
            if (expired != null)
            {
                return expired;
            }
            deps.clock().sleep(RETRY_MILLIS);
        }
    }


    /**
     * The window's end: {@link #START_WINDOW_MILLIS} while the primary is starting (the lock held,
     * no endpoint), else {@link #CONNECT_WINDOW_MILLIS}; {@code null} while it lasts.
     */
    private @Nullable Result expired(long aStartNanos, boolean aStarting)
    {
        long elapsed = elapsedMillis(aStartNanos);
        if (aStarting)
        {
            if (elapsed < START_WINDOW_MILLIS)
            {
                return null;
            }
            LOGGER.log(Level.INFO, "Single instance: the primary holds the lock but wrote no"
                    + " endpoint within " + START_WINDOW_MILLIS + " ms");
            return Result.extra("no-endpoint");
        }
        return elapsed < CONNECT_WINDOW_MILLIS ? null : Result.extra("timeout");
    }


    /**
     * One attempt at an endpoint that names a socket: the directory check, then {@link #attempt}.
     */
    private Result tryEndpoint(Endpoint aEndpoint, List<String> aPaths)
    {
        lastToken = aEndpoint.token().orElse(null);
        Path socket = aEndpoint.socket().orElseThrow();
        Path socketDir = socket.toAbsolutePath().getParent();
        if (socketDir != null && !socketDir.equals(instanceDir.toAbsolutePath()))
        {
            String why = deps.dirCheck().verifyStrictOwnerOnly(socketDir, instanceDir);
            if (why != null)
            {
                LOGGER.log(Level.INFO, "Single instance: socket directory refused: " + why);
                return Result.extra("dir-check");
            }
        }
        return attempt(socket, aEndpoint.token().orElse(""), aPaths);
    }


    /** Step 3 of the class comment. */
    private Result lockWait() throws InterruptedException
    {
        long start = deps.clock().nanoTime();
        while (true)
        {
            switch (deps.lockProbe().tryLock(lockFile))
            {
            case ACQUIRED ->
            {
                InstanceGate.deleteStaleEndpoint(endpointFile);
                return Result.of(Step.PRIMARY);
            }
            case UNSUPPORTED ->
            {
                return Result.extra("no-lock-support");
            }
            default ->
            {
                // held: the old primary is still saving, or a new one took over (below)
            }
            }
            Optional<Endpoint> endpoint = Endpoint.read(endpointFile);
            if (endpoint.isPresent())
            {
                Endpoint e = endpoint.get();
                if (e.noListener())
                {
                    return Result.extra("no-listener");
                }
                String token = e.token().orElse("");
                if (!token.equals(lastToken))
                {
                    return Result.of(Step.NEW_TOKEN);
                }
            }
            if (elapsedMillis(start) >= LOCK_WAIT_MILLIS)
            {
                return Result.extra("timeout");
            }
            deps.clock().sleep(RETRY_MILLIS);
        }
    }


    /** One connection: connect, handshake, OPEN, replies. */
    private Result attempt(Path aSocket, String aToken, List<String> aPaths)
    {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX))
        {
            try (CloseWatchdog.Guard _ = deps.watchdog().guard(channel, REPLY_GAP_MILLIS))
            {
                channel.connect(UnixDomainSocketAddress.of(aSocket));
            }
            catch (IOException e)
            {
                LOGGER.log(Level.DEBUG, "Single instance: connect to " + aSocket + ": " + e);
                return Result.of(Step.CONNECT_FAILED);
            }
            write(channel, WireCodec.hello(aToken));
            List<String> hello = WireCodec.lines(read(channel));
            if (WireCodec.DENIED.equals(hello.get(0)))
            {
                return denied(hello);
            }
            if (!hello.get(0).startsWith(WireCodec.PROTOCOL + " ") || hello.size() < 2)
            {
                LOGGER.log(Level.INFO, "Single instance: unexpected hello: " + hello.get(0));
                return Result.extra("protocol");
            }
            closeSplash();
            grantForeground(hello.get(1));
            write(channel, WireCodec.open(aPaths));
            return replies(channel);
        }
        catch (ReplyTimeout e)
        {
            LOGGER.log(Level.INFO, "Single instance: " + e.getMessage());
            return Result.extra("timeout");
        }
        catch (EOFException e)
        {
            // the primary died (or closed on us): wait for its lock rather than start at once
            LOGGER.log(Level.INFO, "Single instance: the primary closed the connection; waiting"
                    + " for its lock");
            return Result.of(Step.WAIT_LOCK);
        }
        catch (IOException e)
        {
            LOGGER.log(Level.INFO, "Single instance: connection failed: " + e);
            return Result.of(Step.CONNECT_FAILED);
        }
    }


    private Result replies(SocketChannel aChannel) throws IOException
    {
        long opened = deps.clock().nanoTime();
        boolean capped = true;
        while (true)
        {
            List<String> reply = WireCodec.lines(read(aChannel));
            switch (reply.get(0))
            {
            case WireCodec.ACCEPTED ->
            {
                return Result.of(Step.HANDED_OFF);
            }
            case WireCodec.CLOSING ->
            {
                LOGGER.log(Level.INFO,
                        "Single instance: the primary is exiting; waiting for its lock");
                return Result.of(Step.WAIT_LOCK);
            }
            case WireCodec.DENIED ->
            {
                // a refused OPEN (WireCodec.DENIED_REQUEST): this start opens its files itself
                String reason = reply.size() > 1 ? reply.get(1) : "";
                LOGGER.log(Level.INFO,
                        "Single instance: the primary refused the files (" + reason + ")");
                return Result.extra("denied-" + reason);
            }
            case WireCodec.BUSY ->
            {
                String state = reply.size() > 1 ? reply.get(1) : "";
                if (WireCodec.STATE_QUEUED.equals(state))
                {
                    capped = false;
                }
                else if (capped && elapsedMillis(opened) >= START_CAP_MILLIS)
                {
                    LOGGER.log(Level.INFO, "Single instance: the primary did not finish starting"
                            + " within " + START_CAP_MILLIS + " ms");
                    return Result.extra("start-cap");
                }
            }
            default ->
            {
                LOGGER.log(Level.INFO, "Single instance: unexpected reply: " + reply.get(0));
                return Result.extra("protocol");
            }
            }
        }
    }


    private static Result denied(List<String> aHello)
    {
        String reason = aHello.size() > 1 ? aHello.get(1) : "";
        if (WireCodec.DENIED_TOKEN.equals(reason))
        {
            return Result.of(Step.RETRY);
        }
        LOGGER.log(Level.INFO, "Single instance: the primary denied the hand-off (" + reason + ")");
        return Result.extra("denied-" + reason);
    }


    private void closeSplash()
    {
        try
        {
            deps.splashCloser().run();
        }
        catch (RuntimeException | Error e)
        {
            LOGGER.log(Level.DEBUG, "Single instance: closing the splash screen: " + e);
        }
    }


    private void grantForeground(String aPid)
    {
        long pid;
        try
        {
            pid = Long.parseLong(aPid.strip());
        }
        catch (NumberFormatException e)
        {
            LOGGER.log(Level.INFO, "Single instance: no pid in the hello back: " + aPid);
            return;
        }
        try
        {
            ForegroundGrant.Result r = deps.grant().allow(pid);
            LOGGER.log(Level.INFO,
                    "Single instance: foreground grant to pid " + pid + ": "
                            + (r.bool() != 0 ? "granted" : "refused") + " (last error "
                            + r.lastError() + ")");
        }
        catch (ForegroundGrant.NativeCallException | RuntimeException | Error t)
        {
            LOGGER.log(Level.INFO, "Single instance: foreground grant unavailable: " + t);
        }
    }


    private void write(SocketChannel aChannel, String aFrame) throws IOException
    {
        try (CloseWatchdog.Guard _ = deps.watchdog().guard(aChannel, REPLY_GAP_MILLIS))
        {
            WireCodec.write(aChannel, aFrame);
        }
        catch (ClosedChannelException e)
        {
            throw new ReplyTimeout(e);
        }
    }


    private String read(SocketChannel aChannel) throws IOException
    {
        try (CloseWatchdog.Guard _ = deps.watchdog().guard(aChannel, REPLY_GAP_MILLIS))
        {
            return WireCodec.read(aChannel);
        }
        catch (ClosedChannelException e)
        {
            throw new ReplyTimeout(e);
        }
    }


    private long elapsedMillis(long aSinceNanos)
    {
        return TimeUnit.NANOSECONDS.toMillis(deps.clock().nanoTime() - aSinceNanos);
    }

}
