package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import net.cumba.bootstrap.instance.InstanceGate.Deps;
import net.cumba.bootstrap.instance.InstanceGate.Kind;
import net.cumba.bootstrap.instance.InstanceGate.Outcome;
import net.cumba.bootstrap.itmain.StandInServer;
import net.cumba.bootstrap.itmain.StandInServer.Script;
import net.cumba.bootstrap.itmain.StandInServer.Script.End;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hand-off client against a stand-in v1 server over a real Unix domain socket, every decision
 * on the fake clock, the lock on a scripted probe, the directory checks on a fake, the watchdog on
 * a manual scheduler: no real sleep anywhere.
 */
// the stand-in servers are closed in tearDown or by their try-with-resources
@SuppressWarnings("PMD.CloseResource")
class HandOffClientTest
{

    private static final String BUSY_STARTING = WireCodec.BUSY + "\n" + WireCodec.STATE_STARTING;

    private static final String BUSY_QUEUED = WireCodec.BUSY + "\n" + WireCodec.STATE_QUEUED;

    /** A lock probe answering a script of statuses, the last one repeated. */
    static final class ScriptedLock implements LockProbe
    {

        private final List<Status> script;

        final List<Path> files = new ArrayList<>();

        ScriptedLock(Status... aScript)
        {
            script = List.of(aScript);
        }


        @Override
        public Status tryLock(Path aLockFile)
        {
            files.add(aLockFile);
            int i = Math.min(files.size() - 1, script.size() - 1);
            return script.get(i);
        }
    }


    /** A directory check answering a fixed reason (null = fine). */
    static final class FakeDirCheck implements DirCheck
    {

        @Nullable
        String ensureReason;

        @Nullable
        String strictReason;

        final List<Path> ensured = new ArrayList<>();

        final List<Path> verified = new ArrayList<>();

        final List<Path> references = new ArrayList<>();

        @Override
        public @Nullable String ensureOwnerOnly(Path aDir)
        {
            ensured.add(aDir);
            return ensureReason;
        }


        @Override
        public @Nullable String verifyStrictOwnerOnly(Path aDir, Path aOwnDir)
        {
            verified.add(aDir);
            references.add(aOwnDir);
            return strictReason;
        }
    }

    @TempDir
    Path tmp;

    private Path scope;

    private Path instanceDir;

    private FakeClock clock;

    private ManualScheduler scheduler;

    private FakeDirCheck dirCheck;

    private final List<Long> granted = new ArrayList<>();

    private final AtomicInteger splashClosed = new AtomicInteger();

    private @Nullable StandInServer server;

    private Path file1;

    private Path file2;

    /** What the client logged: the only place that says why a start went EXTRA. */
    private LogCapture log;

    @BeforeEach
    void setUp() throws IOException
    {
        scope = tmp.resolve("settings");
        instanceDir = scope.resolve(Endpoint.DIR_NAME);
        Files.createDirectories(instanceDir);
        clock = new FakeClock();
        scheduler = new ManualScheduler();
        dirCheck = new FakeDirCheck();
        file1 = Files.writeString(tmp.resolve("a.csv"), "x");
        file2 = Files.writeString(tmp.resolve("b.csv"), "y");
        log = LogCapture.on(HandOffClient.class);
    }


    @AfterEach
    void tearDown() throws IOException
    {
        log.close();
        if (server != null)
        {
            server.close();
        }
    }


    private Deps deps(LockProbe aLock)
    {
        return deps(aLock, aPid ->
        {
            granted.add(aPid);
            return new ForegroundGrant.Result(1, 0);
        });
    }


    private Deps deps(LockProbe aLock, ForegroundGrant aGrant)
    {
        return new Deps(clock, aLock, dirCheck, Platform.LINUX, aGrant,
                new CloseWatchdog(scheduler), splashClosed::incrementAndGet);
    }


    private StandInServer serve(Function<Integer, Script> aScripts) throws IOException
    {
        server = new StandInServer(instanceDir, instanceDir, aScripts);
        return server;
    }


    private StandInServer serve(Script aScript) throws IOException
    {
        return serve(n -> aScript);
    }


    private Outcome enter(LockProbe aLock, String... aArgs)
    {
        return InstanceGate.enter(scope, aArgs, tmp, deps(aLock));
    }


    private Outcome enterHeld(String... aArgs)
    {
        return enter(new ScriptedLock(LockProbe.Status.HELD), aArgs);
    }


    private static void assertExtra(Outcome aOutcome, String aReason)
    {
        assertEquals(Kind.EXTRA, aOutcome.kind(), aOutcome.toString());
        assertEquals(aReason, aOutcome.reason());
        assertEquals("EXTRA:" + aReason, aOutcome.token());
    }

    // ---- the happy paths ------------------------------------------------------------------


    @Test
    void acceptedIsAHandOff() throws Exception
    {
        StandInServer s = serve(Script.accepted());
        Outcome o = enterHeld(file1.toString(), "b.csv", "--new-instance", "missing.csv");
        assertEquals(Kind.HANDED_OFF, o.kind());
        assertEquals(List.of(List.of(file1.toString(), file2.toString())), s.opens(),
                "existing files only, resolved against the working directory, options skipped");
        assertEquals(List.of(s.token()), s.hellos());
        assertEquals(1, splashClosed.get(), "the splash closes on the hello back");
        assertEquals(List.of(ProcessHandle.current().pid()), granted,
                "the foreground grant goes to the server's pid");
        log.assertLogged(Level.INFO, "foreground grant to pid " + ProcessHandle.current().pid()
                + ": granted (last error 0)");
        assertEquals(0, scheduler.pending(), "every watchdog guard was closed");
        assertEquals(0, clock.sleeps(), "no retry was needed");
    }


    @Test
    void startingHeartbeatsAreWaitedForUnderTheCap() throws Exception
    {
        clock.stepPerReading(20_000);
        serve(Script.accepted(BUSY_STARTING, BUSY_STARTING));
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind(),
                "two heartbeats at 20 s each stay under the 60 s cap");
    }


    @Test
    void startingPastTheCapIsExtra() throws Exception
    {
        clock.stepPerReading(30_000);
        serve(Script.ending(End.HANG, BUSY_STARTING, BUSY_STARTING, BUSY_STARTING));
        assertExtra(enterHeld(file1.toString()), "start-cap");
        log.assertLogged(Level.INFO, "the primary did not finish starting within "
                + HandOffClient.START_CAP_MILLIS + " ms");
        log.assertLogged(Level.INFO, "no hand-off (start-cap); starting as a separate instance");
    }


    @Test
    void aHeartbeatAtExactlyTheCapEndsTheWait() throws Exception
    {
        // the cap is inclusive: the third heartbeat is read at exactly 60 s (one clock reading
        // per heartbeat, 20 s each, from the OPEN) - EXTRA there, the ACCEPTED behind it unread
        clock.stepPerReading(20_000);
        serve(Script.accepted(BUSY_STARTING, BUSY_STARTING, BUSY_STARTING));
        assertExtra(enterHeld(file1.toString()), "start-cap");
    }


    @Test
    void aBusyWithoutOrWithAnUnknownStateCountsAsStarting() throws Exception
    {
        clock.stepPerReading(30_000);
        serve(Script.ending(End.HANG, WireCodec.BUSY, WireCodec.BUSY + "\nWHATEVER",
                WireCodec.BUSY));
        assertExtra(enterHeld(file1.toString()), "start-cap");
    }


    @Test
    void queuedHeartbeatsHaveNoCap() throws Exception
    {
        clock.stepPerReading(30_000);
        String[] replies = new String[12];
        Arrays.fill(replies, BUSY_QUEUED);
        serve(Script.accepted(replies));
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind(),
                "6 minutes of a modal dialog: the user's time, still handed off");
    }


    @Test
    void queuedThenStartingIsNotCappedEither() throws Exception
    {
        // once the primary reached ACCEPTING it cannot go back to STARTING; a later
        // STARTING-shaped heartbeat is not capped any more
        clock.stepPerReading(30_000);
        serve(Script.accepted(BUSY_QUEUED, BUSY_STARTING, BUSY_STARTING, BUSY_STARTING));
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind());
    }


    @Test
    void anEmptyArgumentListStillHandsOff() throws Exception
    {
        StandInServer s = serve(Script.accepted());
        assertEquals(Kind.HANDED_OFF, enterHeld().kind());
        assertEquals(List.of(List.of()), s.opens(), "'come to the front'");
    }

    // ---- CLOSING, EOF, the lock wait ----------------------------------------------------------


    @Test
    void closingThenAFreeLockIsPrimary() throws Exception
    {
        serve(Script.ending(End.CLOSING));
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.HELD,
                LockProbe.Status.HELD, LockProbe.Status.ACQUIRED);
        Outcome o = enter(lock, file1.toString());
        assertEquals(Kind.PRIMARY, o.kind());
        assertEquals(instanceDir, o.instanceDir());
        assertFalse(Files.exists(instanceDir.resolve(Endpoint.FILE_NAME)),
                "the stale endpoint is deleted right after the lock was taken");
        assertEquals(4, lock.files.size(), "gate probe + 3 in the wait");
        log.assertLogged(Level.INFO, "the primary is exiting; waiting for its lock");
        assertTrue(lock.files.stream()
                .allMatch(f -> f.equals(instanceDir.resolve(Endpoint.LOCK_FILE_NAME))));
    }


    @Test
    void eofIsTreatedLikeClosing() throws Exception
    {
        serve(Script.ending(End.EOF));
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.HELD,
                LockProbe.Status.ACQUIRED);
        assertEquals(Kind.PRIMARY, enter(lock, file1.toString()).kind());
        log.assertLogged(Level.INFO, "the primary closed the connection; waiting for its lock");
    }


    @Test
    void theLockWaitExpiresIntoExtra() throws Exception
    {
        serve(Script.ending(End.CLOSING));
        Outcome o = enterHeld(file1.toString());
        assertExtra(o, "timeout");
        assertEquals(HandOffClient.LOCK_WAIT_MILLIS / HandOffClient.RETRY_MILLIS, clock.sleeps(),
                "exactly the 30 s lock wait: it ends the moment it has lasted that long");
    }


    /** Rewrites the endpoint to name {@code aServer} (what a new primary does after its lock). */
    private void pointEndpointAt(StandInServer aServer)
    {
        try
        {
            StandInServer.writeEndpoint(instanceDir, aServer.socketPath(), aServer.token());
        }
        catch (IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
    }


    @Test
    void aNewTokenDuringTheWaitMeansAThirdStartTookTheLock() throws Exception
    {
        // a: the old primary, exiting; b: the third start, now primary, its socket in a fallback
        // directory (so the strict check runs on it too)
        try (StandInServer a = new StandInServer(instanceDir, instanceDir,
                n -> Script.ending(End.CLOSING));
                StandInServer b = new StandInServer(instanceDir, instanceDir.resolve("b"),
                        n -> Script.accepted()))
        {
            pointEndpointAt(a);
            AtomicInteger probes = new AtomicInteger();
            LockProbe lock = f ->
            {
                if (probes.incrementAndGet() == 3)
                {
                    pointEndpointAt(b);
                }
                return LockProbe.Status.HELD;
            };
            Outcome o = enter(lock, file1.toString());
            assertEquals(Kind.HANDED_OFF, o.kind(), o.toString());
            assertEquals(List.of(List.of(file1.toString())), a.opens(), "round 1 went to a");
            assertEquals(List.of(List.of(file1.toString())), b.opens(), "round 2 went to b");
            assertEquals(List.of(instanceDir.resolve("b")), dirCheck.verified,
                    "b's socket directory is not the instance directory: verified strictly");
            assertEquals(2, splashClosed.get(), "a hello back per round");
            log.assertLogged(Level.INFO, "a new primary took the lock during the wait; handing"
                    + " off to it (round 2)");
        }
    }


    @Test
    void atMostThreeRoundsThenExtra() throws Exception
    {
        // every primary says CLOSING, and during every wait a new one takes the lock
        Function<Integer, Script> closing = n -> Script.ending(End.CLOSING);
        try (StandInServer s0 = new StandInServer(instanceDir, instanceDir, closing);
                StandInServer s1 = new StandInServer(instanceDir, instanceDir, closing);
                StandInServer s2 = new StandInServer(instanceDir, instanceDir, closing);
                StandInServer s3 = new StandInServer(instanceDir, instanceDir, closing))
        {
            List<StandInServer> servers = List.of(s0, s1, s2, s3);
            pointEndpointAt(s0);
            AtomicInteger probes = new AtomicInteger();
            LockProbe rotating = f ->
            {
                int n = probes.incrementAndGet();
                if (n % 2 == 0 && n / 2 < servers.size())
                {
                    pointEndpointAt(servers.get(n / 2));
                }
                return LockProbe.Status.HELD;
            };
            assertExtra(enter(rotating, file1.toString()), "rounds");
            for (int i = 0; i < 3; i++)
            {
                assertEquals(1, servers.get(i).opens().size(), "round " + (i + 1));
            }
            assertEquals(0, s3.opens().size(), "no fourth round");
            log.assertLogged(Level.INFO, "handing off to it (round 2)");
            log.assertLogged(Level.INFO, "handing off to it (round 3)");
            log.assertLogged(Level.INFO, "no hand-off (rounds); starting as a separate instance");
        }
    }


    @Test
    void noLockSupportDuringTheWaitIsExtra() throws Exception
    {
        serve(Script.ending(End.CLOSING));
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.UNSUPPORTED);
        assertExtra(enter(lock, file1.toString()), "no-lock-support");
    }


    @Test
    void listenerNoneDuringTheWaitIsExtra() throws Exception
    {
        serve(Script.ending(End.CLOSING));
        AtomicInteger p = new AtomicInteger();
        LockProbe lock = f ->
        {
            if (p.incrementAndGet() == 2)
            {
                try
                {
                    Files.writeString(instanceDir.resolve(Endpoint.FILE_NAME),
                            "listener=none\nversion=1\n", StandardCharsets.UTF_8);
                }
                catch (IOException e)
                {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            return LockProbe.Status.HELD;
        };
        assertExtra(enter(lock, file1.toString()), "no-listener");
    }

    // ---- the connect window ---------------------------------------------------------------


    @Test
    void noEndpointAndTheLockHeldExpiresIntoExtra()
    {
        // review M2: the lock holder is alive but wrote no endpoint - the long window, then EXTRA
        Outcome o = enterHeld(file1.toString());
        assertExtra(o, "no-endpoint");
        assertEquals(HandOffClient.START_WINDOW_MILLIS / HandOffClient.RETRY_MILLIS, clock.sleeps(),
                "exactly the 30 s window");
        log.assertLogged(Level.INFO, "the primary holds the lock but wrote no endpoint within "
                + HandOffClient.START_WINDOW_MILLIS + " ms");
    }


    @Test
    void aColdPrimaryThatListensAfterTwentyFiveSecondsStillGetsTheFiles() throws Exception
    {
        // review M2: the motivating case - the app was not running, five files were opened at
        // once, and the primary's cold start (class loading, a virus scanner) takes 25 s to its
        // endpoint. The lock is held all along, so the primary is alive: no EXTRA at 10 s.
        StandInServer s = new StandInServer(tmp.resolve("not-yet"), instanceDir,
                n -> Script.accepted());
        server = s;
        AtomicInteger sleeps = new AtomicInteger();
        clock.onSleep(() ->
        {
            if (sleeps.incrementAndGet() == 250)
            {
                pointEndpointAt(s);
            }
        });
        Outcome o = enterHeld(file1.toString());
        assertEquals(Kind.HANDED_OFF, o.kind(), o.toString());
        assertEquals(List.of(List.of(file1.toString())), s.opens());
        assertEquals(250, clock.sleeps(), "waited the 25 s, not a moment longer");
    }


    @Test
    void aRefusedSocketKeepsTheShortWindowEvenWithTheLockHeld() throws Exception
    {
        // only "lock held and no endpoint" extends the window; a refused socket (a stale
        // endpoint, or a listener that died) still gives up after 10 s
        StandInServer.writeEndpoint(instanceDir, instanceDir.resolve("s-gone"), "t");
        assertExtra(enterHeld(file1.toString()), "timeout");
        assertEquals(HandOffClient.CONNECT_WINDOW_MILLIS / HandOffClient.RETRY_MILLIS,
                clock.sleeps(), "exactly the 10 s window");
        log.assertLogged(Level.FINE, "connect to " + instanceDir.resolve("s-gone") + ": ");
        log.assertLogged(Level.INFO, "no hand-off (timeout); starting as a separate instance");
    }


    @Test
    void aStaleListenerNoneIsReadAgainBeforeGoingExtra() throws Exception
    {
        // review LOW 11: the previous run could not listen and left listener=none; a new primary
        // took the lock and deletes that file a moment later. A client reading it in between
        // must not go EXTRA on the stale file.
        Files.writeString(instanceDir.resolve(Endpoint.FILE_NAME), "listener=none\nversion=1\n",
                StandardCharsets.UTF_8);
        StandInServer s = new StandInServer(tmp.resolve("not-yet"), instanceDir,
                n -> Script.accepted());
        server = s;
        clock.onSleep(() -> pointEndpointAt(s));
        Outcome o = enterHeld(file1.toString());
        assertEquals(Kind.HANDED_OFF, o.kind(), o.toString());
        assertEquals(1, clock.sleeps(), "read once more after one pause");
    }


    @Test
    void aPathWithALineBreakIsNeverSent() throws Exception
    {
        // review LOW 4: the frame splits on \n and strips a trailing \r, so such a path would
        // reach the primary as other paths; this start opens its files itself instead
        Path lf = Files.writeString(tmp.resolve("line\nbreak.csv"), "x");
        Path cr = Files.writeString(tmp.resolve("carriage\r"), "x");
        StandInServer s = serve(Script.accepted());
        assertExtra(enterHeld(file1.toString(), lf.toString()), "path-newline");
        assertExtra(enterHeld(cr.toString()), "path-newline");
        assertEquals(List.of(), s.hellos(), "never connected");
        assertEquals(List.of(), s.opens(), "never sent");
    }


    @Test
    void aDeniedOpenIsExtraAtOnce() throws Exception
    {
        // the server refuses an OPEN it cannot take with DENIED <reason> (review LOW 4, server
        // half): a separate instance at once, never the lock wait
        StandInServer s = serve(
                Script.ending(End.EOF, WireCodec.DENIED + "\n" + WireCodec.DENIED_REQUEST));
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD);
        assertExtra(enter(lock, file1.toString()), "denied-request");
        assertEquals(1, s.opens().size());
        assertEquals(1, lock.files.size(), "the gate's probe only: no lock wait");
        assertEquals(0, clock.sleeps());
        log.assertLogged(Level.INFO, "the primary refused the files (request)");
        server.close();
        serve(Script.ending(End.EOF, WireCodec.DENIED));
        assertExtra(enterHeld(file1.toString()), "denied-");
        log.assertLogged(Level.INFO, "the primary refused the files ()");
    }


    @Test
    void aDeniedHelloWithoutAReasonIsExtra() throws Exception
    {
        // a bare DENIED (no reason line) on the hello: refused, not a protocol error
        serve(Script.hello(WireCodec.DENIED));
        assertExtra(enterHeld(file1.toString()), "denied-");
        log.assertLogged(Level.INFO, "the primary denied the hand-off ()");
        assertEquals(0, clock.sleeps(), "no retry: only DENIED token is retried");
    }


    @Test
    void aBrokenFrameFromThePrimaryIsAFailedConnection() throws Exception
    {
        // not an EOF, not a time-out: a reply whose length prefix is over the cap. The attempt
        // counts as a failed connection, so the lock is tried - here free: PRIMARY
        Path socket = instanceDir.resolve(Endpoint.SOCKET_PREFIX + "raw");
        try (ServerSocketChannel raw = ServerSocketChannel.open(StandardProtocolFamily.UNIX))
        {
            raw.bind(UnixDomainSocketAddress.of(socket));
            StandInServer.writeEndpoint(instanceDir, socket, "t");
            CompletableFuture<String> peer = CompletableFuture.supplyAsync(() ->
            {
                try (SocketChannel c = raw.accept())
                {
                    String hello = WireCodec.read(c);
                    c.write(ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).flip());
                    return hello;
                }
                catch (IOException e)
                {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.ACQUIRED);
            Outcome o = enter(lock, file1.toString());
            assertEquals(Kind.PRIMARY, o.kind(), o.toString());
            assertEquals(WireCodec.hello("t"), peer.get(10, TimeUnit.SECONDS));
            assertEquals(2, lock.files.size(), "the gate's probe + one after the failed attempt");
            log.assertLogged(Level.INFO, "connection failed: java.io.IOException: frame length "
                    + Integer.MAX_VALUE + " exceeds the cap");
        }
        finally
        {
            Files.deleteIfExists(socket);
        }
    }


    @Test
    void noEndpointButTheLockTurnsFreeIsPrimary() throws Exception
    {
        Files.writeString(instanceDir.resolve(Endpoint.FILE_NAME), "garbage",
                StandardCharsets.UTF_8);
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.HELD,
                LockProbe.Status.HELD, LockProbe.Status.ACQUIRED);
        Outcome o = enter(lock, file1.toString());
        assertEquals(Kind.PRIMARY, o.kind(), "the old primary died before it listened");
        assertFalse(Files.exists(instanceDir.resolve(Endpoint.FILE_NAME)));
        assertEquals(2, clock.sleeps(), "the gate's probe, then two held probes in the window");
    }


    @Test
    void aRefusedSocketAndTheLockTurningFreeIsPrimary() throws Exception
    {
        StandInServer.writeEndpoint(instanceDir, instanceDir.resolve("s-gone"), "t");
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.HELD,
                LockProbe.Status.ACQUIRED);
        assertEquals(Kind.PRIMARY, enter(lock, file1.toString()).kind());
        assertEquals(0, splashClosed.get(), "no hello back, the splash stays");
    }


    @Test
    void noLockSupportInTheWindowIsExtra()
    {
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD, LockProbe.Status.UNSUPPORTED);
        assertExtra(enter(lock, file1.toString()), "no-lock-support");
    }


    @Test
    void aStaleTokenIsDeniedAndTheReReadSucceeds() throws Exception
    {
        StandInServer s = serve(Script.accepted());
        // a stale endpoint: the live socket, the previous run's token
        StandInServer.writeEndpoint(instanceDir, s.socketPath(), "stale-token");
        // the new primary writes its endpoint "a moment later" - while the client pauses
        // between attempts (a live server answered, so no lock probe happens on a denial)
        clock.onSleep(() -> pointEndpointAt(s));
        Outcome o = enterHeld(file1.toString());
        assertEquals(Kind.HANDED_OFF, o.kind(), o.toString());
        assertEquals(List.of("stale-token", s.token()), s.hellos());
        assertEquals(1, splashClosed.get(), "closed once, on the accepted hello");
        assertEquals(1, clock.sleeps(), "retried once after the denial");
    }


    @Test
    void deniedVersionAndDeniedLimitAreExtraAtOnce() throws Exception
    {
        serve(Script.denied(WireCodec.DENIED_VERSION));
        assertExtra(enterHeld(file1.toString()), "denied-version");
        log.assertLogged(Level.INFO, "the primary denied the hand-off (version)");
        server.close();
        serve(Script.denied(WireCodec.DENIED_LIMIT));
        assertExtra(enterHeld(file1.toString()), "denied-limit");
        server.close();
        serve(Script.denied("something-new"));
        assertExtra(enterHeld(file1.toString()), "denied-something-new");
        assertEquals(0, splashClosed.get(), "never a hello back");
        assertEquals(0, clock.sleeps(), "no retry");
    }


    @Test
    void listenerNoneIsExtraWithoutAConnect() throws Exception
    {
        Files.writeString(instanceDir.resolve(Endpoint.FILE_NAME), "listener=none\nversion=1\n",
                StandardCharsets.UTF_8);
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.HELD);
        assertExtra(enter(lock, file1.toString()), "no-listener");
        assertEquals(1, clock.sleeps(), "confirmed by a second read (review LOW 11)");
        assertEquals(2, lock.files.size(), "the gate's probe + one on the first sighting");
        assertEquals(0, scheduler.delays().size(), "no connect attempt");
    }


    @Test
    void aSocketOutsideTheInstanceDirectoryIsVerifiedStrictly() throws Exception
    {
        Path run = instanceDir.resolve("fallback");
        StandInServer s = new StandInServer(instanceDir, run, n -> Script.accepted());
        server = s;
        dirCheck.strictReason = run + " is owned by somebody";
        assertExtra(enterHeld(file1.toString()), "dir-check");
        log.assertLogged(Level.INFO, "socket directory refused: " + run + " is owned by somebody");
        assertEquals(List.of(run), dirCheck.verified);
        assertEquals(List.of(instanceDir), dirCheck.references,
                "the owner to match is the instance directory's (review LOW 6)");
        assertEquals(0, s.hellos().size(), "refused before connecting");
        dirCheck.strictReason = null;
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind());
        assertEquals(List.of(run, run), dirCheck.verified);
    }


    @Test
    void aSocketInTheInstanceDirectoryIsNotVerifiedAgain() throws Exception
    {
        serve(Script.accepted());
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind());
        assertEquals(List.of(), dirCheck.verified);
        assertEquals(List.of(instanceDir), dirCheck.ensured, "the instance directory itself");
    }


    @Test
    void anUnexpectedHelloOrReplyIsExtra() throws Exception
    {
        serve(Script.hello("HTTP/1.1 200 OK\nnope"));
        assertExtra(enterHeld(file1.toString()), "protocol");
        log.assertLogged(Level.INFO, "unexpected hello: HTTP/1.1 200 OK");
        server.close();
        serve(Script.hello(WireCodec.PROTOCOL + " 1"));
        assertExtra(enterHeld(file1.toString()), "protocol");
        server.close();
        serve(Script.accepted("WHAT\nIS THIS"));
        assertExtra(enterHeld(file1.toString()), "protocol");
        log.assertLogged(Level.INFO, "unexpected reply: WHAT");
    }


    @Test
    void aPidThatIsNotANumberIsLoggedAndTheHandOffContinues() throws Exception
    {
        serve(new Script(WireCodec.PROTOCOL + " 1\nnot-a-pid", List.of(), End.ACCEPTED));
        assertEquals(Kind.HANDED_OFF, enterHeld(file1.toString()).kind());
        assertEquals(List.of(), granted);
        log.assertLogged(Level.INFO, "no pid in the hello back: not-a-pid");
    }


    @Test
    void aFailingGrantIsLoggedAndTheHandOffContinues() throws Exception
    {
        serve(Script.accepted());
        ForegroundGrant failing = aPid ->
        {
            throw new UnsatisfiedLinkError("no user32 here");
        };
        Outcome o = InstanceGate.enter(scope, new String[]
        {
                file1.toString()
        }, tmp, deps(new ScriptedLock(LockProbe.Status.HELD), failing));
        assertEquals(Kind.HANDED_OFF, o.kind());
        log.assertLogged(Level.INFO,
                "foreground grant unavailable: java.lang.UnsatisfiedLinkError: no user32 here");
        ForegroundGrant refused = aPid -> new ForegroundGrant.Result(0, 5);
        server.close();
        serve(Script.accepted());
        assertEquals(Kind.HANDED_OFF, InstanceGate.enter(scope, new String[]
        {
                file1.toString()
        }, tmp, deps(new ScriptedLock(LockProbe.Status.HELD), refused)).kind());
        log.assertLogged(Level.INFO, "foreground grant to pid " + ProcessHandle.current().pid()
                + ": refused (last error 5)");
    }


    @Test
    void aSplashCloserThatThrowsDoesNotEndTheHandOff() throws Exception
    {
        serve(Script.accepted());
        Deps d = new Deps(clock, new ScriptedLock(LockProbe.Status.HELD), dirCheck, Platform.LINUX,
                ForegroundGrant.NONE, new CloseWatchdog(scheduler), () ->
                {
                    throw new IllegalStateException("headless after all");
                });
        assertEquals(Kind.HANDED_OFF, InstanceGate.enter(scope, new String[]
        {
                file1.toString()
        }, tmp, d).kind());
        log.assertLogged(Level.FINE, "closing the splash screen: java.lang.IllegalStateException:"
                + " headless after all");
    }

    // ---- the watchdog ---------------------------------------------------------------------


    @Test
    void aServerThatNeverAnswersIsTimedOutByTheWatchdog() throws Exception
    {
        StandInServer s = serve(Script.ending(End.HANG));
        CompletableFuture<Outcome> client = CompletableFuture
                .supplyAsync(() -> enterHeld(file1.toString()));
        s.awaitHanging();
        waitForPending(5);
        assertEquals(List.of(HandOffClient.REPLY_GAP_MILLIS),
                List.of(scheduler.delays().get(scheduler.delays().size() - 1)),
                "the read is guarded by the reply gap");
        assertEquals(1, scheduler.fireAll(), "the watchdog closes the channel");
        assertExtra(client.get(10, TimeUnit.SECONDS), "timeout");
        log.assertLogged(Level.INFO,
                "Single instance: no reply within " + HandOffClient.REPLY_GAP_MILLIS + " ms");
    }


    @Test
    void anOpenWriteThatNeverCompletesIsTimedOutByTheWatchdog() throws Exception
    {
        // an OPEN far above the socket's send buffer against a server that never reads: the
        // write blocks. 256 paths of ~3 KiB (a real file system caps a path near 4 KiB).
        StandInServer s = serve(Script.ending(End.NO_READ));
        Path deep = tmp.resolve("big");
        for (int i = 0; i < 15; i++)
        {
            deep = deep.resolve("d".repeat(200));
        }
        Files.createDirectories(deep);
        List<String> args = new ArrayList<>();
        for (int i = 0; i < WireCodec.MAX_PATHS; i++)
        {
            Path f = deep.resolve("f" + i);
            Files.writeString(f, "");
            args.add(f.toString());
        }
        assertTrue(WireCodec.open(args).length() > 700_000, "well above a socket buffer");
        CompletableFuture<Outcome> client = CompletableFuture
                .supplyAsync(() -> enterHeld(args.toArray(new String[0])));
        s.awaitHanging();
        waitForPending(4);
        assertEquals(1, scheduler.fireAll(), "the watchdog closes the channel mid-write");
        assertExtra(client.get(10, TimeUnit.SECONDS), "timeout");
    }


    /**
     * Waits (real time, bounded) until the client armed its {@code aArmed}-th guard and is blocked
     * under it. The guards are armed in order: connect, hello write, hello read, OPEN write, then
     * one per reply read.
     */
    private void waitForPending(int aArmed) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while ((scheduler.delays().size() < aArmed || scheduler.pending() == 0)
                && System.nanoTime() < deadline)
        {
            Thread.sleep(5);
        }
        assertEquals(aArmed, scheduler.delays().size(), "guards armed so far");
        assertEquals(1, scheduler.pending(), "the blocked call is guarded");
    }


    @Test
    void interruptedWhileWaitingIsExtra()
    {
        // like Thread.sleep, the fake throws with the flag cleared: the client must set it again
        try
        {
            Deps d = new Deps(new Clock()
            {

                @Override
                public long nanoTime()
                {
                    return clock.nanoTime();
                }


                @Override
                public void sleep(long aMillis) throws InterruptedException
                {
                    throw new InterruptedException();
                }
            }, new ScriptedLock(LockProbe.Status.HELD), dirCheck, Platform.LINUX,
                    ForegroundGrant.NONE, new CloseWatchdog(scheduler), () ->
                    {
                    });
            assertExtra(InstanceGate.enter(scope, new String[0], tmp, d), "interrupted");
            assertTrue(Thread.currentThread().isInterrupted(), "the flag is restored");
        }
        finally
        {
            // clear it again for the next test on this thread
            Thread.interrupted();
        }
    }
}
